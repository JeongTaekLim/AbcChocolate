package AbcChocolate;

import java.io.IOException;
import java.util.ArrayList;

/**
 * 
 * @author JeongTaek
 * @brief StdAudio���� �о���� .wav ������ FFT ������ �迭 ���·� ��ȯ�ϴ� Ŭ����
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

	private static final int FREQ_12 = 0; // 4��
	private static final int FREQ_15 = 1; // 4��
	private static final int FREQ_18 = 2; // 4��
	private static final int FREQ_20 = 3; // 4��

	private static final int MONO = 1;
	private static final int STEREO = 2;
	
	private static final int START_SAMPLE = 10000;
	private static final int END_SAMPLE = 25000;

	private String filePath = "";
	private String label; // SVM ���̺�

	// WINDOW_SIZE(512)�� ���� FFT ��ü ����
	FFTCalculator mFftCalculator = new FFTCalculator(WINDOW_SIZE);

	int[] arrFreq = { 12000, 15000, 18000, 20000 };
	int[] arrTargetFreqSamplePos;

	public FeatureExtractor2(String filePath, String label) {
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
	 * @throws ExtractException 
	 */
	public String getSvmFeature(int channel) throws IOException, ExtractException {
		WavReader mWavReader = new WavReader();
		// input wav ���� ������ �о����
		double[] mixedData = mWavReader.read(this.filePath);
		double[] leftData = null;
		double[] rightData = null;

		// ���׷����� ���
		if (channel == MONO) {
			leftData = mixedData;	// input wav ���� ���� �Ҹ�������
			rightData = mixedData;	// input wav ���� ���� �Ҹ�������
		} else if (channel == STEREO) {
			leftData = getLeftSound(mixedData);
			rightData = getRightSound(mixedData);
		} else {
			System.out.println("channel ����");
		}

		double[] fLeftData = filter(leftData);
		double[] fRightData = filter(rightData);

		// ���� 11kHz�� ���� ��ũ�� ����
		// int syncIdx = getSync(leftData, 0, leftData.length, 1);
		int leftSync = getSync(fLeftData, START_SAMPLE, END_SAMPLE, 1);
		int rightSync = getSync(fRightData, START_SAMPLE, END_SAMPLE, 1);

		System.out.println("���� left 	11kHz ��ũ : " + leftSync);
		System.out.println("���� right 	11kHz ��ũ : " + rightSync);

		// 11kHz 1��, 12kHz 4��, 15kHz 4��, 18kHz 4��, 20kHz 4��
		int[] maxIdxs = new int[4 + 4 + 4 + 4];

		// 17kHz, 18kHz, 19kHz, 20kHz�� ���� wav���ĵ��� �ϴ� �� ��Ƶ� ����Ʈ
		ArrayList<double[]> LtmpWavFeatures = new ArrayList<double[]>();
		ArrayList<double[]> RtmpWavFeatures = new ArrayList<double[]>();

		// �ܼ� pilot 1��(11kHz 1��)�� ������ ������ 12, 15, 18, 20��κ��� ���ĸ� �̾Ƴ���.
		for (int i = 0; i < 4 + 4 + 4 + 4; i++) {
			// ����
			int maxIdx = getMaxIdx(fLeftData, leftSync,	leftSync + SYNC_LENGTH + 10000 + (EMIT_LENGTH + MUTE_LENGTH) * i);
			// ������ maxIdx ���� 200���� ����
			double[] wavFeature = sliceArr(fLeftData, maxIdx + 1, maxIdx + 1 + SLICE_LENGTH);
			// feature�� features�� ����
			LtmpWavFeatures.add(wavFeature);

			// ����
			maxIdx = getMaxIdx(fRightData, rightSync,
					rightSync + SYNC_LENGTH + 10000 + (EMIT_LENGTH + MUTE_LENGTH) * i);
			wavFeature = sliceArr(fRightData, maxIdx + 1, maxIdx + 1 + SLICE_LENGTH);
			RtmpWavFeatures.add(wavFeature);
		}

		// LtmpWavFeatures���� FFT�Ͽ� ���� ����Ʈ
		ArrayList<double[]> LtmpFftFeatures = new ArrayList<double[]>();

		// ����
		for (int i = 0; i < LtmpWavFeatures.size(); i++) {
			double[] tmpWavFeature = LtmpWavFeatures.get(i);
			double[] real = new double[WINDOW_SIZE];
			double[] imag = new double[WINDOW_SIZE];
			for (int j = 0; j < tmpWavFeature.length; j++) {
				real[j] = tmpWavFeature[j];
			}
			// FFT
			mFftCalculator.fft(real, imag);

			// FFT ���ݸ� ���ο� �迭�� �Ű� ����
			double[] tmpFftFeature = new double[WINDOW_SIZE / 2];

			// FFT ��� ����
			for (int j = 0; j < tmpFftFeature.length; j++) {
				tmpFftFeature[j] = getAmplitude(real, imag, j);
			}
			// LfftTmpFeatures�� �߰�.
			LtmpFftFeatures.add(tmpFftFeature);
		}

		// tmpWavFeatures���� FFT�Ͽ� ���� ����Ʈ(������ ����)
		ArrayList<double[]> RtmpFftFeatures = new ArrayList<double[]>();

		// ����
		for (int i = 0; i < RtmpWavFeatures.size(); i++) {
			double[] tmpWavFeature = RtmpWavFeatures.get(i);
			double[] real = new double[WINDOW_SIZE];
			double[] imag = new double[WINDOW_SIZE];
			for (int j = 0; j < tmpWavFeature.length; j++) {
				real[j] = tmpWavFeature[j];
			}
			// FFT
			mFftCalculator.fft(real, imag);

			// FFT ���ݸ� ���ο� �迭�� �Ű� ����
			double[] tmpFftFeature = new double[WINDOW_SIZE / 2];

			// FFT ��� ����
			for (int j = 0; j < tmpFftFeature.length; j++) {
				tmpFftFeature[j] = getAmplitude(real, imag, j);
			}
			// fftTmpFeatures�� �߰�.
			RtmpFftFeatures.add(tmpFftFeature);
		}

		// 16�� ��ũ���� 200���� ���� FFT ������ ������ �� ������, 4���� ���� ����� ����� �Ѵ�.
		// ���� fftTmpFeatures���� 16���� fft������� ����ִ�
		// fftFeatures���� 4���� ����� ������� �����̴�.
		ArrayList<double[]> LfftFeatures = new ArrayList<double[]>();

		for (int i = 0; i < 4; i++) {
			double[] fftFeature = new double[WINDOW_SIZE / 2];
			for (int j = 0; j < LtmpFftFeatures.get(0).length; j++) {
				try {
					fftFeature[j] = LtmpFftFeatures.get(i * 4 + 0)[j] + LtmpFftFeatures.get(i * 4 + 1)[j]
							+ LtmpFftFeatures.get(i * 4 + 2)[j] + LtmpFftFeatures.get(i * 4 + 3)[j];
				} catch (Exception e) {
					System.out.println("fftFeature 4�� ��ճ��� �������� �ε��� ���� �߻�");
				}
			}
			// fftFeatures�� fftFeature�� ��´�.
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
					System.out.println("fftFeature 4�� ��ճ��� �������� �ε��� ���� �߻�");
				}
			}
			// fftFeatures�� fftFeature�� ��´�.
			RfftFeatures.add(fftFeature);
		}

		String svmFeature = "";
		int prefix = 1;
		// ����
		for (int i = 0; i < LfftFeatures.size(); i++) {
			double[] fftFeature = LfftFeatures.get(i);
			
			// 22050Hz�� 256���� ���� �����ǹǷ� 1���� ���� �� 86Hz�� �Ҵ�ȴ�. -10�� ���ִ� ������ 17kHz -
			// 86*10Hz���� svm���ͷ� �����ϱ� �����̴�.
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
		// ����
		for (int i = 0; i < RfftFeatures.size(); i++) {
			double[] fftFeature = RfftFeatures.get(i);
			// 22050Hz�� 256���� ���� �����ǹǷ� 1���� ���� �� 86Hz�� �Ҵ�ȴ�. -10�� ���ִ� ������ 17kHz -
			// 86*10Hz���� svm���ͷ� �����ϱ� �����̴�.
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

	private double getAmplitude(double[] real, double[] imag, int idx) {
		double result = 0;
		result = Math.sqrt(real[idx] * real[idx] + imag[idx] * imag[idx]);
		return result;
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
	 * @throws ExtractException 
	 */
	private int getSync(double[] input, int start, int end, int gap) throws ExtractException {
		int fftcnt = 0;
		// fft ������� Ư�� ���ļ� �뿪�뿡 �ش��ϴ� value���� ���� ������ �迭
		// double fftResults[][] = new double[5][input.length];
		int maxIdx = 0;
		double maxValue = 0;
		for (int inputIdx = start; inputIdx < end; inputIdx = inputIdx + gap) {
			// ���� ���ø����� window ��ȸ
			int windowIdx = 0;
			double windowSum = 0;
			double[] real = new double[WINDOW_SIZE];
			double[] imag = new double[WINDOW_SIZE];

			for (int i = inputIdx; i < inputIdx + SYNC_LENGTH; i++) {
				// window�� input ������ ��� ���
				if (i >= end)
					break;

				real[windowIdx] = input[i];
				windowIdx++;
			}
			// �� window�� ���Ͽ� FFT
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

	// sync ���� �Ҹ� �ִ밪�� ��Ÿ���� ���� ��Ƴ���.
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
