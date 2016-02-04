package AbcChocolate;

import java.io.IOException;
import java.util.ArrayList;

/**
 * 
 * @author JeongTaek
 * @brief StdAudio���� �о���� .wav ������ FFT ������ �迭 ���·� ��ȯ�ϴ� Ŭ����
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

	private static final int FREQ_16 = 0; // 2��
	private static final int FREQ_17 = 1; // 4��
	private static final int FREQ_18 = 2; // 4��
	private static final int FREQ_19 = 3; // 4��
	private static final int FREQ_20 = 4; // 4��

	private static final int MONO = 1;
	private static final int STEREO = 2;

	private String filePath = "";
	private String label; // SVM ���̺�

	// WINDOW_SIZE(512)�� ���� FFT ��ü ����
	FFTCalculator mFftCalculator = new FFTCalculator(WINDOW_SIZE);

	int[] arrFreq = { 16000, 17000, 18000, 19000, 20000 };
	int[] arrTargetFreqSamplePos;

	public FeatureExtractor(String filePath, String label) {
		this.filePath = filePath;
		this.label = label;
		arrTargetFreqSamplePos = new int[arrFreq.length];
		// Ÿ���� �Ǵ� ���ļ��� ���� ��ġ�� ������
		for (int arrFreqIdx = 0; arrFreqIdx < arrFreq.length; arrFreqIdx++) {
			arrTargetFreqSamplePos[arrFreqIdx] = (arrFreq[arrFreqIdx] * (WINDOW_SIZE / 2)) / (SAMPLING_RATE / 2);
		}
	}

	/**
	 * @brief svm ���ĸ� �̾Ƴ���.
	 * @return
	 * @throws IOException
	 */
	public String getSvmFeature(int channel) throws IOException {
		WavReader mWavReader = new WavReader();
		// input wav ���� ������ �о����
		double[] mixedData = mWavReader.read(this.filePath);
		double[] leftData = null;
		double[] rightData = null;

		// ���׷����� ���
		if (channel == MONO) {
			// input wav ���� ���� �Ҹ�������
			leftData = mixedData;
			rightData = mixedData;
		} else if (channel == STEREO) {
			leftData = getLeftSound(mixedData);
			rightData = getRightSound(mixedData);
		} else {
			System.out.println("channel ����");
		}

		double[] filtered = filter(leftData);

		// ���� 16kHz�� ���� ��ũ�� ����
		// int syncIdx = getSync(leftData, 0, leftData.length, 1);
		int syncIdx = getSync(leftData, 0, leftData.length, 1);
		// syncIdx = getSync(leftData, syncIdx - 5, syncIdx + 5, 1);

		System.out.println("���� 16kHz ��ũ : " + syncIdx);

		// 16kHz 2��, 17kHz 4��, 18kHz 4��, 19kHz 4��, 20kHz 4��
		int[] maxIdxs = new int[2 + 4 + 4 + 4 + 4];

		// 17kHz, 18kHz, 19kHz, 20kHz�� ���� wav���ĵ��� �ϴ� �� ��Ƶ� ����Ʈ
		ArrayList<double[]> tmpWavFeatures = new ArrayList<double[]>();

		// �ܼ� pilot 2��(16kHz 2��)�� ������ ������ 17, 18, 19, 20��κ��� ���ĸ� �̾Ƴ���.
		for (int i = 2; i < maxIdxs.length; i++) {
			// sytncIdx+ 0, 4100, 8200, 12300, ... �� ���ؼ� ���� maxIdx�� ����
			int maxIdx = getMaxIdx(filtered, syncIdx, syncIdx + (MUTE_LENGTH + SOUND_LENGTH) * i);
			// ������ maxIdx ���� 100���� ����
			double[] wavFeature = sliceArr(filtered, maxIdx + 1, maxIdx + 1 + FEATURE_LENGTH);
			// feature�� features�� ����
			tmpWavFeatures.add(wavFeature);
		}

		// tmpWavFeatures���� FFT�Ͽ� ���� ����Ʈ
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

			// FFT ���ݸ� ���ο� �迭�� �Ű� ����
			double[] tmpFftFeature = new double[WINDOW_SIZE / 2];

			// FFT ��� ���ó���ؾ���.
			for (int j = 0; j < tmpFftFeature.length; j++) {
				tmpFftFeature[j] = Math.abs(real[j]);
			}
			// fftTmpFeatures�� �߰�.
			tmpFftFeatures.add(tmpFftFeature);
		}

		// 16�� ��ũ���� 100���� ���� FFT ������ ������ �� ������, 4���� ���� ����� ����� �Ѵ�.
		// ���� fftTmpFeatures���� 16���� fft������� ����ִ�
		// fftFeatures���� 4���� ����� ������� �����̴�.
		ArrayList<double[]> fftFeatures = new ArrayList<double[]>();

		for (int i = 0; i < 4; i++) {
			double[] fftFeature = new double[WINDOW_SIZE / 2];
			for (int j = 0; j < WINDOW_SIZE / 2; j++) {
				fftFeature[j] = tmpFftFeatures.get(i * 4 + 0)[j] + tmpFftFeatures.get(i * 4 + 1)[j]
						+ tmpFftFeatures.get(i * 4 + 2)[j] + tmpFftFeatures.get(i * 4 + 3)[j];
			}
			// fftFeatures�� fftFeature�� ��´�.
			fftFeatures.add(fftFeature);
		}

		String svmFeature = "";
		int prefix = 1;
		for (int i = 0; i < fftFeatures.size(); i++) {
			double[] fftFeature = fftFeatures.get(i);
			// 22050Hz�� 256���� ���� �����ǹǷ� 1���� ���� �� 86Hz�� �Ҵ�ȴ�. -6�� ���ִ� ������ 17kHz -
			// 86*6Hz���� svm���ͷ� �����ϱ� �����̴�.
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
	 *            : ������ ���� double �迭
	 * @param start
	 *            : start �̻�
	 * @param end
	 *            : end �̸�
	 * @return ����
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

				// ���� ���ø����� window ��ȸ
				int windowIdx = 0;
				double windowSum = 0;

				double[] real = new double[WINDOW_SIZE];
				double[] imag = new double[WINDOW_SIZE];

				for (int i = inputIdx; i < inputIdx + SOUND_LENGTH; i++) {
					// window�� input ������ ��� ���
					if (i >= end)
						break;

					real[windowIdx] = input[i];
					windowIdx++;
				}
				// �� window�� ���Ͽ� FFT
				mFftCalculator.fft(real, imag);

				// ���ļ� �뿪���� sum �� ����� fftResults�� ������(5�� : 16,17,18,19,20)
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
	 *            ���� wav �迭
	 * @param start:
	 *            Ž�� ���� �ε���
	 * @param end
	 *            : Ž�� �� �ε���
	 * @param gap
	 *            : Ž�� gap
	 * @return
	 */
	private int getSync(double[] input, int start, int end, int gap) {
		int fftcnt = 0;

		// fft ������� Ư�� ���ļ� �뿪�뿡 �ش��ϴ� value���� ���� ������ �迭
		double fftResults[][] = new double[5][input.length];

		for (int inputIdx = start; inputIdx < end; inputIdx = inputIdx + gap) {

			// ���� ���ø����� window ��ȸ
			int windowIdx = 0;
			double windowSum = 0;

			double[] real = new double[WINDOW_SIZE];
			double[] imag = new double[WINDOW_SIZE];

			for (int i = inputIdx; i < inputIdx + SOUND_LENGTH; i++) {
				// window�� input ������ ��� ���
				if (i >= end)
					break;

				real[windowIdx] = input[i];
				windowIdx++;
			}
			// �� window�� ���Ͽ� FFT
			mFftCalculator.fft(real, imag);
			fftcnt++;

			// ���ļ� �뿪���� sum �� ����� fftResults�� ������(5�� : 16,17,18,19,20)
			for (int j = 0; j < 5; j++) {
				windowSum = getSum(real, imag, arrTargetFreqSamplePos[j] - 1, arrTargetFreqSamplePos[j] + 1 + 1);
				fftResults[j][inputIdx] = windowSum;
			}

		}

		int syncIdx = 0;
		double maxValue = 0;

		// fftResult �м�
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

	// sync ���� �Ҹ� �ִ밪�� ��Ÿ���� ���� ��Ƴ���.
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

	// �Է� �迭�� start �̻�, end �̸����� �߶󳽴�.
	public double[] sliceArr(double[] input, int start, int end) {
		if (start >= end) {
			System.out.println("Error : sliceArr, start>=end �����Դϴ�.");
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

	// ����н� ���͸� ��ģ ����� �������ִ� �޼ҵ�
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
