package AbcChocolate;

import java.io.IOException;
import java.util.ArrayList;

/**
 * 
 * @author JeongTaek
 * @brief StdAudio에서 읽어들인 .wav 파일을 FFT 가능한 배열 형태로 변환하는 클래스
 * @see StdAudio
 */
public class FeatureExtractor {

	private static final int WINDOW_SIZE = 512;
	private static final int SOUND_LENGTH = 100;
	private static final int FEATURE_LENGTH = 100;

	private static final int LEFT = 0;
	private static final int RIGHT = 1;

	private static final int SAMPLING_RATE = 44100;
	private static final int MUTE_LENGTH = 4000;

	private static final int FREQ_16 = 0; // 2개
	private static final int FREQ_17 = 1; // 4개
	private static final int FREQ_18 = 2; // 4개
	private static final int FREQ_19 = 3; // 4개
	private static final int FREQ_20 = 4; // 4개

	private static final int MONO = 1;
	private static final int STEREO = 2;

	private String filePath = "";
	private String label; // SVM 레이블

	// WINDOW_SIZE(512)를 위한 FFT 객체 생성
	FFTCalculator mFftCalculator = new FFTCalculator(WINDOW_SIZE);

	int[] arrFreq = { 16000, 17000, 18000, 19000, 20000 };
	int[] arrTargetFreqSamplePos;

	public FeatureExtractor(String filePath, String label) {
		this.filePath = filePath;
		this.label = label;
		arrTargetFreqSamplePos = new int[arrFreq.length];
		// 타겟이 되는 주파수의 샘플 위치를 저장함
		for (int arrFreqIdx = 0; arrFreqIdx < arrFreq.length; arrFreqIdx++) {
			arrTargetFreqSamplePos[arrFreqIdx] = (arrFreq[arrFreqIdx] * (WINDOW_SIZE / 2)) / (SAMPLING_RATE / 2);
		}
	}

	/**
	 * @brief svm 피쳐를 뽑아낸다.
	 * @return
	 * @throws IOException
	 */
	public String getSvmFeature(int channel) throws IOException {
		WavReader mWavReader = new WavReader();
		// input wav 파일 끝까지 읽어들임
		double[] mixedData = mWavReader.read(this.filePath);
		double[] leftData = null;
		double[] rightData = null;

		// 스테레오일 경우
		if (channel == MONO) {
			// input wav 파일 좌측 소리데이터
			leftData = mixedData;
			rightData = mixedData;
		} else if (channel == STEREO) {
			leftData = getLeftSound(mixedData);
			rightData = getRightSound(mixedData);
		} else {
			System.out.println("channel 오류");
		}

		double[] filtered = filter(leftData);

		// 최초 16kHz에 대한 싱크를 얻어옴
		// int syncIdx = getSync(leftData, 0, leftData.length, 1);
		int syncIdx = getSync(leftData, 0, leftData.length, 1);
		// syncIdx = getSync(leftData, syncIdx - 5, syncIdx + 5, 1);

		System.out.println("최초 16kHz 싱크 : " + syncIdx);

		// 16kHz 2개, 17kHz 4개, 18kHz 4개, 19kHz 4개, 20kHz 4개
		int[] maxIdxs = new int[2 + 4 + 4 + 4 + 4];

		// 17kHz, 18kHz, 19kHz, 20kHz에 대한 wav피쳐들을 일단 막 담아둘 리스트
		ArrayList<double[]> tmpWavFeatures = new ArrayList<double[]>();

		// 단순 pilot 2개(16kHz 2개)를 제외한 나머지 17, 18, 19, 20들로부터 피쳐를 뽑아낸다.
		for (int i = 2; i < maxIdxs.length; i++) {
			// sytncIdx+ 0, 4100, 8200, 12300, ... 에 대해서 각각 maxIdx를 구함
			int maxIdx = getMaxIdx(filtered, syncIdx, syncIdx + (MUTE_LENGTH + SOUND_LENGTH) * i);
			// 구해진 maxIdx 이후 100개를 취함
			double[] wavFeature = sliceArr(filtered, maxIdx + 1, maxIdx + 1 + FEATURE_LENGTH);
			// feature를 features로 삽입
			tmpWavFeatures.add(wavFeature);
		}

		// tmpWavFeatures들을 FFT하여 담을 리스트
		ArrayList<double[]> tmpFftFeatures = new ArrayList<double[]>();

		for (int i = 0; i < tmpWavFeatures.size(); i++) {
			double[] tmpWavFeature = tmpWavFeatures.get(i);
			double[] real = new double[WINDOW_SIZE];
			double[] imag = new double[WINDOW_SIZE];
			for (int j = 0; j < tmpWavFeature.length; j++) {
				real[j] = tmpWavFeature[j];
			}
			// FFT
			mFftCalculator.fft(real, imag);

			// FFT 절반만 새로운 배열에 옮겨 담음
			double[] tmpFftFeature = new double[WINDOW_SIZE / 2];

			// FFT 결과 양수처리해야함.
			for (int j = 0; j < tmpFftFeature.length; j++) {
				tmpFftFeature[j] = Math.abs(real[j]);
			}
			// fftTmpFeatures에 추가.
			tmpFftFeatures.add(tmpFftFeature);
		}

		// 16개 피크이후 100개에 대한 FFT 수행을 마무리 한 다음엔, 4개씩 묶어 평균을 내줘야 한다.
		// 현재 fftTmpFeatures에는 16개의 fft결과들이 들어있다
		// fftFeatures에는 4개의 결과가 들어있을 예정이다.
		ArrayList<double[]> fftFeatures = new ArrayList<double[]>();

		for (int i = 0; i < 4; i++) {
			double[] fftFeature = new double[WINDOW_SIZE / 2];
			for (int j = 0; j < WINDOW_SIZE / 2; j++) {
				fftFeature[j] = tmpFftFeatures.get(i * 4 + 0)[j] + tmpFftFeatures.get(i * 4 + 1)[j]
						+ tmpFftFeatures.get(i * 4 + 2)[j] + tmpFftFeatures.get(i * 4 + 3)[j];
			}
			// fftFeatures에 fftFeature를 담는다.
			fftFeatures.add(fftFeature);
		}

		String svmFeature = "";
		int prefix = 1;
		for (int i = 0; i < fftFeatures.size(); i++) {
			double[] fftFeature = fftFeatures.get(i);
			// 22050Hz가 256개의 점에 대응되므로 1개의 점에 약 86Hz가 할당된다. -6을 해주는 이유는 17kHz -
			// 86*6Hz부터 svm벡터로 삽입하기 위함이다.
			for (int j = arrTargetFreqSamplePos[FREQ_17] - 6; j < WINDOW_SIZE / 2; j++) {
				svmFeature = svmFeature + Integer.toString(prefix) + ":" + Double.toString(fftFeature[j]) + " ";
				prefix++;
			}
		}
		svmFeature = label + " " + svmFeature;
		System.out.println("SVM Feature: " + svmFeature);

		return svmFeature;

	}

	public double[][] getStereoArray(double[] mixedData) {

		double[][] stereoData;

		stereoData = new double[2][mixedData.length / 2];
		int posLeft = 0, posRight = 0;
		for (int i = 0; i < mixedData.length; i++) {
			if (i % 2 == 0)
				stereoData[LEFT][posLeft++] = mixedData[i];
			else
				stereoData[RIGHT][posRight++] = mixedData[i];
		}
		return stereoData;
	}

	public double[] getLeftSound(double[] mixedData) {
		double[] result = new double[mixedData.length / 2];
		int pos = 0;
		for (int i = 0; i < mixedData.length; i = i + 2) {
			result[pos++] = mixedData[i];
		}
		return result;
	}

	public double[] getRightSound(double[] mixedData) {

		double[] result = new double[mixedData.length / 2];
		int pos = 0;
		for (int i = 1; i < mixedData.length; i = i + 2) {
			result[pos++] = mixedData[i];
		}
		return result;
	}

	/**
	 * @param input
	 *            : 총합을 구할 double 배열
	 * @param start
	 *            : start 이상
	 * @param end
	 *            : end 미만
	 * @return 총합
	 */
	private double getSum(double[] real, double[] imag, int start, int end) {
		double result = 0;
		for (int i = start; i < end; i++) {
			result = result + Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
		}
		return result;
	}

	private class FftResultSetter extends Thread {
		double[] input;
		double[][] fftResults;
		int start, end, gap;

		public FftResultSetter(double[] input, double[][] fftResults, int start, int end, int gap) {
			this.input = input;
			this.fftResults = fftResults;
			this.start = start;
			this.end = end;
			this.gap = gap;
		}

		public void run() {

			for (int inputIdx = start; inputIdx < end; inputIdx = inputIdx + gap) {

				// 각각 샘플마다의 window 순회
				int windowIdx = 0;
				double windowSum = 0;

				double[] real = new double[WINDOW_SIZE];
				double[] imag = new double[WINDOW_SIZE];

				for (int i = inputIdx; i < inputIdx + SOUND_LENGTH; i++) {
					// window가 input 범위를 벗어날 경우
					if (i >= end)
						break;

					real[windowIdx] = input[i];
					windowIdx++;
				}
				// 한 window에 대하여 FFT
				mFftCalculator.fft(real, imag);

				// 주파수 대역별로 sum 한 결괄를 fftResults에 저장함(5개 : 16,17,18,19,20)
				for (int j = 0; j < 5; j++) {
					windowSum = getSum(real, imag, arrTargetFreqSamplePos[j] - 1, arrTargetFreqSamplePos[j] + 1 + 1);
					fftResults[j][inputIdx] = windowSum;
				}

			}
		}
	}

	/**
	 * 
	 * @param input:
	 *            원본 wav 배열
	 * @param start:
	 *            탐색 시작 인덱스
	 * @param end
	 *            : 탐색 끝 인덱스
	 * @param gap
	 *            : 탐색 gap
	 * @return
	 */
	private int getSync(double[] input, int start, int end, int gap) {
		int fftcnt = 0;

		// fft 결과에서 특정 주파수 대역대에 해당하는 value들의 합을 저장할 배열
		double fftResults[][] = new double[5][input.length];

		for (int inputIdx = start; inputIdx < end; inputIdx = inputIdx + gap) {

			// 각각 샘플마다의 window 순회
			int windowIdx = 0;
			double windowSum = 0;

			double[] real = new double[WINDOW_SIZE];
			double[] imag = new double[WINDOW_SIZE];

			for (int i = inputIdx; i < inputIdx + SOUND_LENGTH; i++) {
				// window가 input 범위를 벗어날 경우
				if (i >= end)
					break;

				real[windowIdx] = input[i];
				windowIdx++;
			}
			// 한 window에 대하여 FFT
			mFftCalculator.fft(real, imag);
			fftcnt++;

			// 주파수 대역별로 sum 한 결괄를 fftResults에 저장함(5개 : 16,17,18,19,20)
			for (int j = 0; j < 5; j++) {
				windowSum = getSum(real, imag, arrTargetFreqSamplePos[j] - 1, arrTargetFreqSamplePos[j] + 1 + 1);
				fftResults[j][inputIdx] = windowSum;
			}

		}

		int syncIdx = 0;
		double maxValue = 0;

		// fftResult 분석
		for (int inputIdx = start; inputIdx < end; inputIdx = inputIdx + gap) {
			double tmpValue = 0;

			for (int j = 0; j < 2 + 4 + 4 + 4 + 4; j++) {

				if (j < 2 && (inputIdx + (MUTE_LENGTH + SOUND_LENGTH) * j) < end) {

					tmpValue = tmpValue + fftResults[FREQ_16][inputIdx + (MUTE_LENGTH + SOUND_LENGTH) * j];

				} else if (j < 2 + 4 && (inputIdx + (MUTE_LENGTH + SOUND_LENGTH) * j) < end) {

					tmpValue = tmpValue + fftResults[FREQ_17][inputIdx + (MUTE_LENGTH + SOUND_LENGTH) * j];

				} else if (j < 2 + 4 + 4 && (inputIdx + (MUTE_LENGTH + SOUND_LENGTH) * j) < end) {

					tmpValue = tmpValue + fftResults[FREQ_18][inputIdx + (MUTE_LENGTH + SOUND_LENGTH) * j];

				} else if (j < 2 + 4 + 4 + 4 && (inputIdx + (MUTE_LENGTH + SOUND_LENGTH) * j) < end) {

					tmpValue = tmpValue + fftResults[FREQ_19][inputIdx + (MUTE_LENGTH + SOUND_LENGTH) * j];

				} else if (j < 2 + 4 + 4 + 4 + 4 && (inputIdx + (MUTE_LENGTH + SOUND_LENGTH) * j) < end) {

					tmpValue = tmpValue + fftResults[FREQ_20][inputIdx + (MUTE_LENGTH + SOUND_LENGTH) * j];

				} else {
					break;
				}

			}
			if (tmpValue > maxValue) {
				maxValue = tmpValue;
				syncIdx = inputIdx;
			}
		}

		System.out.println("FFT count: " + fftcnt);
		return syncIdx;

	}

	// sync 이후 소리 최대값을 나타내는 점을 잡아낸다.
	public int getMaxIdx(double[] input, int startIdx, int endIdx) {
		int maxIdx = 0;
		double maxValue = 0;
		for (int i = startIdx; i < endIdx; i++) {
			if (input[i] >= maxValue) {
				maxIdx = i;
				maxValue = input[i];
			}
		}

		return maxIdx;
	}

	// 입력 배열을 start 이상, end 미만으로 잘라낸다.
	public double[] sliceArr(double[] input, int start, int end) {
		if (start >= end) {
			System.out.println("Error : sliceArr, start>=end 에러입니다.");
			return null;
		}
		double[] result = new double[end - start];
		int tmpIdx = 0;
		for (int i = start; i < end; i++) {
			result[tmpIdx] = input[i];
			tmpIdx++;
		}
		return result;
	}

	// 밴드패스 필터를 거친 결과를 리턴해주는 메소드
	public double[] filter(double[] input) {

		double arrBandPassFilter[] = { -0.0014, 0.0023, -0.0024, 0.0007, 0.0025, -0.006, 0.0075, -0.0059, 0.0019,
				0.0023, -0.0044, 0.0036, -0.0014, -0.0001, -0.0006, 0.0027, -0.0043, 0.0039, -0.0019, -0.0001, 0.0002,
				0.0014, -0.003, 0.0028, -0.0008, -0.0014, 0.0019, -0.0004, -0.0014, 0.0015, 0.0005, -0.003, 0.0039,
				-0.0024, 0.0002, 0.0004, 0.0014, -0.0042, 0.0054, -0.0038, 0.0009, 0.0004, 0.0011, -0.0041, 0.0055,
				-0.0037, 0.0001, 0.0021, -0.0011, -0.0021, 0.004, -0.0022, -0.0021, 0.0053, -0.0047, 0.0011, 0.0015,
				-0.0002, -0.0046, 0.0087, -0.0084, 0.0041, -0.0002, 0.0006, -0.0055, 0.0103, -0.0102, 0.005, 0.0008,
				-0.0017, -0.003, 0.0086, -0.0089, 0.0027, 0.0051, -0.0078, 0.0032, 0.0036, -0.0049, -0.002, 0.012,
				-0.0164, 0.0116, -0.0026, -0.0009, -0.006, 0.0182, -0.0246, 0.019, -0.0062, -0.0012, -0.0047, 0.0194,
				-0.0285, 0.0215, -0.0026, -0.0119, 0.0082, 0.0106, -0.0251, 0.0165, 0.014, -0.0433, 0.0454, -0.017,
				-0.0125, 0.0019, 0.0665, -0.164, 0.2272, -0.201, 0.0804, 0.0804, -0.201, 0.2272, -0.164, 0.0665, 0.0019,
				-0.0125, -0.017, 0.0454, -0.0433, 0.014, 0.0165, -0.0251, 0.0106, 0.0082, -0.0119, -0.0026, 0.0215,
				-0.0285, 0.0194, -0.0047, -0.0012, -0.0062, 0.019, -0.0246, 0.0182, -0.006, -0.0009, -0.0026, 0.0116,
				-0.0164, 0.012, -0.002, -0.0049, 0.0036, 0.0032, -0.0078, 0.0051, 0.0027, -0.0089, 0.0086, -0.003,
				-0.0017, 0.0008, 0.005, -0.0102, 0.0103, -0.0055, 0.0006, -0.0002, 0.0041, -0.0084, 0.0087, -0.0046,
				-0.0002, 0.0015, 0.0011, -0.0047, 0.0053, -0.0021, -0.0022, 0.004, -0.0021, -0.0011, 0.0021, 0.0001,
				-0.0037, 0.0055, -0.0041, 0.0011, 0.0004, 0.0009, -0.0038, 0.0054, -0.0042, 0.0014, 0.0004, 0.0002,
				-0.0024, 0.0039, -0.003, 0.0005, 0.0015, -0.0014, -0.0004, 0.0019, -0.0014, -0.0008, 0.0028, -0.003,
				0.0014, 0.0002, -0.0001, -0.0019, 0.0039, -0.0043, 0.0027, -0.0006, -0.0001, -0.0014, 0.0036, -0.0044,
				0.0023, 0.0019, -0.0059, 0.0075, -0.006, 0.0025, 0.0007, -0.0024, 0.0023, -0.0014 };
		double[] result = new double[input.length + arrBandPassFilter.length - 1];

		for (int i = 0; i < input.length; i++) {
			for (int j = 0; j < arrBandPassFilter.length; j++) {
				result[i + j] = input[i] * arrBandPassFilter[j] + result[i + j];
			}
		}

		return result;

	}

}
