package AbcChocolate;

import java.io.IOException;
import java.util.ArrayList;

/**
 * 
 * @author JeongTaek
 * @brief StdAudio에서 읽어들인 .wav 파일을 FFT 가능한 배열 형태로 변환하는 클래스
 * @see StdAudio
 */
public class FeatureExtractor2 {

	private static final int WINDOW_SIZE = 512;
	private static final int SYNC_LENGTH = 500;

	private static final int LEFT = 0;
	private static final int RIGHT = 1;

	private static final int SLICE_LENGTH = 200;
	private static final int EMIT_LENGTH = 100;
	private static final int SAMPLING_RATE = 44100;
	private static final int MUTE_LENGTH = 4500;

	private static final int FREQ_12 = 0; // 4개
	private static final int FREQ_15 = 1; // 4개
	private static final int FREQ_18 = 2; // 4개
	private static final int FREQ_20 = 3; // 4개

	private static final int MONO = 1;
	private static final int STEREO = 2;
	
	private static final int START_SAMPLE = 10000;
	private static final int END_SAMPLE = 25000;

	private String filePath = "";
	private String label; // SVM 레이블

	// WINDOW_SIZE(512)를 위한 FFT 객체 생성
	FFTCalculator mFftCalculator = new FFTCalculator(WINDOW_SIZE);

	int[] arrFreq = { 12000, 15000, 18000, 20000 };
	int[] arrTargetFreqSamplePos;

	public FeatureExtractor2(String filePath, String label) {
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
	 * @throws ExtractException 
	 */
	public String getSvmFeature(int channel) throws IOException, ExtractException {
		WavReader mWavReader = new WavReader();
		// input wav 파일 끝까지 읽어들임
		double[] mixedData = mWavReader.read(this.filePath);
		double[] leftData = null;
		double[] rightData = null;

		// 스테레오일 경우
		if (channel == MONO) {
			leftData = mixedData;	// input wav 파일 좌측 소리데이터
			rightData = mixedData;	// input wav 파일 우측 소리데이터
		} else if (channel == STEREO) {
			leftData = getLeftSound(mixedData);
			rightData = getRightSound(mixedData);
		} else {
			System.out.println("channel 오류");
		}

		double[] fLeftData = filter(leftData);
		double[] fRightData = filter(rightData);

		// 최초 11kHz에 대한 싱크를 얻어옴
		// int syncIdx = getSync(leftData, 0, leftData.length, 1);
		int leftSync = getSync(fLeftData, START_SAMPLE, END_SAMPLE, 1);
		int rightSync = getSync(fRightData, START_SAMPLE, END_SAMPLE, 1);

		System.out.println("최초 left 	11kHz 싱크 : " + leftSync);
		System.out.println("최초 right 	11kHz 싱크 : " + rightSync);

		// 11kHz 1개, 12kHz 4개, 15kHz 4개, 18kHz 4개, 20kHz 4개
		int[] maxIdxs = new int[4 + 4 + 4 + 4];

		// 17kHz, 18kHz, 19kHz, 20kHz에 대한 wav피쳐들을 일단 막 담아둘 리스트
		ArrayList<double[]> LtmpWavFeatures = new ArrayList<double[]>();
		ArrayList<double[]> RtmpWavFeatures = new ArrayList<double[]>();

		// 단순 pilot 1개(11kHz 1개)를 제외한 나머지 12, 15, 18, 20들로부터 피쳐를 뽑아낸다.
		for (int i = 0; i < 4 + 4 + 4 + 4; i++) {
			// 좌측
			int maxIdx = getMaxIdx(fLeftData, leftSync,	leftSync + SYNC_LENGTH + 10000 + (EMIT_LENGTH + MUTE_LENGTH) * i);
			// 구해진 maxIdx 이후 200개를 취함
			double[] wavFeature = sliceArr(fLeftData, maxIdx + 1, maxIdx + 1 + SLICE_LENGTH);
			// feature를 features로 삽입
			LtmpWavFeatures.add(wavFeature);

			// 우측
			maxIdx = getMaxIdx(fRightData, rightSync,
					rightSync + SYNC_LENGTH + 10000 + (EMIT_LENGTH + MUTE_LENGTH) * i);
			wavFeature = sliceArr(fRightData, maxIdx + 1, maxIdx + 1 + SLICE_LENGTH);
			RtmpWavFeatures.add(wavFeature);
		}

		// LtmpWavFeatures들을 FFT하여 담을 리스트
		ArrayList<double[]> LtmpFftFeatures = new ArrayList<double[]>();

		// 좌측
		for (int i = 0; i < LtmpWavFeatures.size(); i++) {
			double[] tmpWavFeature = LtmpWavFeatures.get(i);
			double[] real = new double[WINDOW_SIZE];
			double[] imag = new double[WINDOW_SIZE];
			for (int j = 0; j < tmpWavFeature.length; j++) {
				real[j] = tmpWavFeature[j];
			}
			// FFT
			mFftCalculator.fft(real, imag);

			// FFT 절반만 새로운 배열에 옮겨 담음
			double[] tmpFftFeature = new double[WINDOW_SIZE / 2];

			// FFT 결과 저장
			for (int j = 0; j < tmpFftFeature.length; j++) {
				tmpFftFeature[j] = getAmplitude(real, imag, j);
			}
			// LfftTmpFeatures에 추가.
			LtmpFftFeatures.add(tmpFftFeature);
		}

		// tmpWavFeatures들을 FFT하여 담을 리스트(우측에 대한)
		ArrayList<double[]> RtmpFftFeatures = new ArrayList<double[]>();

		// 우측
		for (int i = 0; i < RtmpWavFeatures.size(); i++) {
			double[] tmpWavFeature = RtmpWavFeatures.get(i);
			double[] real = new double[WINDOW_SIZE];
			double[] imag = new double[WINDOW_SIZE];
			for (int j = 0; j < tmpWavFeature.length; j++) {
				real[j] = tmpWavFeature[j];
			}
			// FFT
			mFftCalculator.fft(real, imag);

			// FFT 절반만 새로운 배열에 옮겨 담음
			double[] tmpFftFeature = new double[WINDOW_SIZE / 2];

			// FFT 결과 저장
			for (int j = 0; j < tmpFftFeature.length; j++) {
				tmpFftFeature[j] = getAmplitude(real, imag, j);
			}
			// fftTmpFeatures에 추가.
			RtmpFftFeatures.add(tmpFftFeature);
		}

		// 16개 피크이후 200개에 대한 FFT 수행을 마무리 한 다음엔, 4개씩 묶어 평균을 내줘야 한다.
		// 현재 fftTmpFeatures에는 16개의 fft결과들이 들어있다
		// fftFeatures에는 4개의 결과가 들어있을 예정이다.
		ArrayList<double[]> LfftFeatures = new ArrayList<double[]>();

		for (int i = 0; i < 4; i++) {
			double[] fftFeature = new double[WINDOW_SIZE / 2];
			for (int j = 0; j < LtmpFftFeatures.get(0).length; j++) {
				try {
					fftFeature[j] = LtmpFftFeatures.get(i * 4 + 0)[j] + LtmpFftFeatures.get(i * 4 + 1)[j]
							+ LtmpFftFeatures.get(i * 4 + 2)[j] + LtmpFftFeatures.get(i * 4 + 3)[j];
				} catch (Exception e) {
					System.out.println("fftFeature 4개 평균내는 과정에서 인덱스 에러 발생");
				}
			}
			// fftFeatures에 fftFeature를 담는다.
			LfftFeatures.add(fftFeature);
		}

		ArrayList<double[]> RfftFeatures = new ArrayList<double[]>();

		for (int i = 0; i < 4; i++) {
			double[] fftFeature = new double[WINDOW_SIZE / 2];
			for (int j = 0; j < RtmpFftFeatures.get(0).length; j++) {
				try {
					fftFeature[j] = RtmpFftFeatures.get(i * 4 + 0)[j] + RtmpFftFeatures.get(i * 4 + 1)[j]
							+ RtmpFftFeatures.get(i * 4 + 2)[j] + RtmpFftFeatures.get(i * 4 + 3)[j];
				} catch (Exception e) {
					System.out.println("fftFeature 4개 평균내는 과정에서 인덱스 에러 발생");
				}
			}
			// fftFeatures에 fftFeature를 담는다.
			RfftFeatures.add(fftFeature);
		}

		String svmFeature = "";
		int prefix = 1;
		// 좌측
		for (int i = 0; i < LfftFeatures.size(); i++) {
			double[] fftFeature = LfftFeatures.get(i);
			
			// 22050Hz가 256개의 점에 대응되므로 1개의 점에 약 86Hz가 할당된다. -10을 해주는 이유는 17kHz -
			// 86*10Hz부터 svm벡터로 삽입하기 위함이다.
			int startPos = 0, endPos = 0;
			switch (i) {
			case FREQ_12:
				startPos = arrTargetFreqSamplePos[FREQ_12] - 10;
				endPos = arrTargetFreqSamplePos[FREQ_12] + 10;
				break;
			case FREQ_15:
				startPos = arrTargetFreqSamplePos[FREQ_15] - 10;
				endPos = arrTargetFreqSamplePos[FREQ_15] + 10;
				break;
			case FREQ_18:
				startPos = arrTargetFreqSamplePos[FREQ_18] - 10;
				endPos = arrTargetFreqSamplePos[FREQ_18] + 10;
				break;
			case FREQ_20:
				startPos = arrTargetFreqSamplePos[FREQ_20] - 10;
				endPos = arrTargetFreqSamplePos[FREQ_20] + 10;
				break;
			}
			normalize(fftFeature, startPos, endPos);
			for (int j = startPos; j < endPos; j++) {
				svmFeature = svmFeature + Integer.toString(prefix) + ":" + Double.toString(fftFeature[j]) + " ";
				prefix++;
			}

		}
		// 우측
		for (int i = 0; i < RfftFeatures.size(); i++) {
			double[] fftFeature = RfftFeatures.get(i);
			// 22050Hz가 256개의 점에 대응되므로 1개의 점에 약 86Hz가 할당된다. -10을 해주는 이유는 17kHz -
			// 86*10Hz부터 svm벡터로 삽입하기 위함이다.
			int startPos = 0, endPos = 0;
			switch (i) {
			case FREQ_12:
				startPos = arrTargetFreqSamplePos[FREQ_12] - 10;
				endPos = arrTargetFreqSamplePos[FREQ_12] + 10;
				break;
			case FREQ_15:
				startPos = arrTargetFreqSamplePos[FREQ_15] - 10;
				endPos = arrTargetFreqSamplePos[FREQ_15] + 10;
				break;
			case FREQ_18:
				startPos = arrTargetFreqSamplePos[FREQ_18] - 10;
				endPos = arrTargetFreqSamplePos[FREQ_18] + 10;
				break;
			case FREQ_20:
				startPos = arrTargetFreqSamplePos[FREQ_20] - 10;
				endPos = arrTargetFreqSamplePos[FREQ_20] + 10;
				break;
			}
			normalize(fftFeature, startPos, endPos);
			for (int j = startPos; j < endPos; j++) {
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

	private double getAmplitude(double[] real, double[] imag, int idx) {
		double result = 0;
		result = Math.sqrt(real[idx] * real[idx] + imag[idx] * imag[idx]);
		return result;
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
	 * @throws ExtractException 
	 */
	private int getSync(double[] input, int start, int end, int gap) throws ExtractException {
		int fftcnt = 0;
		// fft 결과에서 특정 주파수 대역대에 해당하는 value들의 합을 저장할 배열
		// double fftResults[][] = new double[5][input.length];
		int maxIdx = 0;
		double maxValue = 0;
		for (int inputIdx = start; inputIdx < end; inputIdx = inputIdx + gap) {
			// 각각 샘플마다의 window 순회
			int windowIdx = 0;
			double windowSum = 0;
			double[] real = new double[WINDOW_SIZE];
			double[] imag = new double[WINDOW_SIZE];

			for (int i = inputIdx; i < inputIdx + SYNC_LENGTH; i++) {
				// window가 input 범위를 벗어날 경우
				if (i >= end)
					break;

				real[windowIdx] = input[i];
				windowIdx++;
			}
			// 한 window에 대하여 FFT
			mFftCalculator.fft(real, imag);
			fftcnt++;
			windowSum = getSum(real, imag, 0, real.length / 2);
			if (windowSum > maxValue) {
				maxValue = windowSum;
				maxIdx = inputIdx;
			}
		}
		System.out.println("FFT count: " + fftcnt);
		
		if(maxIdx > END_SAMPLE || maxIdx < START_SAMPLE){
			throw new ExtractException("ERROR :: getSync() is failed - maxIdx > END_SAMPLE || maxIdx < START_SAMPLE");
		}
		
		return maxIdx;
	}
	public void normalize(double[] input, int startIdx, int endIdx) throws ExtractException{
		int maxIdx=0;
		double maxValue=0;
		
		for(int i=startIdx;i<endIdx;i++){
			input[i] = input[i] / WINDOW_SIZE;
		}
		
		maxIdx = getMaxIdx(input, startIdx, endIdx);
		maxValue = input[maxIdx];
		for(int i=startIdx;i<endIdx;i++){
			input[i] = input[i] / maxValue;
		}
		return;
	}

	// sync 이후 소리 최대값을 나타내는 점을 잡아낸다.
	public int getMaxIdx(double[] input, int startIdx, int endIdx) throws ExtractException {
		int maxIdx = 0;
		double maxValue = 0;
		try{
			for (int i = startIdx; i < endIdx; i++) {
				if (input[i] >= maxValue) {
					maxIdx = i;
					maxValue = input[i];
				}
			}
		}catch(IndexOutOfBoundsException e){
			throw new ExtractException("ERROR :: getMaxIdx() is failed - IndexOutOfBoundsException"
					+ ", startIdx :: "+startIdx + ", endIdx :: "+endIdx);
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

		double arrBandPassFilter[] = { 0.001249357, -0.001304802, -0.00219841, 0.004881118, -0.000819208, -0.004996001
				, 0.004114648, -0.000967922, 0.003361379, -0.005672403, 0.002138934, -0.000852297, 0.005072683, -0.004682537
				, 2.2408E-05, -0.002266054, 0.00650868, -0.002045592, -0.00125678, -0.004734064, 0.005473458, 0.002375447
				, -0.000924853, -0.006295781, 0.000394753, 0.006873842, 0.001118748, -0.004186, -0.008238637, 0.008868828
				, 0.003037482, 0.003821426, -0.017323359, 0.006862, 0.001074169, 0.01740983, -0.022096117, 0.002338516
				, -0.008711342, 0.032364701, -0.018539205, 0.000349932, -0.027775833, 0.041030837, -0.006080212, 0.008392534
				, -0.053099953, 0.033853165, 0.011253571, 0.03604806, -0.077507428, -0.004581552, 0.026282124, 0.11797702
				, -0.092551694, -0.280753483, 0.532219261, -0.280753483, -0.092551694, 0.11797702, 0.026282124, -0.004581552
				, -0.077507428, 0.03604806, 0.011253571, 0.033853165, -0.053099953, 0.008392534, -0.006080212, 0.041030837
				, -0.027775833, 0.000349932, -0.018539205, 0.032364701, -0.008711342, 0.002338516, -0.022096117, 0.01740983
				, 0.001074169, 0.006862, -0.017323359, 0.003821426, 0.003037482, 0.008868828, -0.008238637, -0.004186
				, 0.001118748, 0.006873842, 0.000394753, -0.006295781, -0.000924853, 0.002375447, 0.005473458, -0.004734064
				, -0.00125678, -0.002045592, 0.00650868, -0.002266054, 2.2408E-05, -0.004682537, 0.005072683, -0.000852297
				, 0.002138934, -0.005672403, 0.003361379, -0.000967922, 0.004114648, -0.004996001, -0.000819208, 0.004881118
				, -0.00219841, -0.001304802, 0.001249357,  };
		double[] result = new double[input.length + arrBandPassFilter.length - 1];

		for (int i = 0; i < input.length; i++) {
			for (int j = 0; j < arrBandPassFilter.length; j++) {
				result[i + j] = input[i] * arrBandPassFilter[j] + result[i + j];
			}
		}

		return result;

	}

}
