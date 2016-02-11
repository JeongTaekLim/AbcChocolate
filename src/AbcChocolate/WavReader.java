package AbcChocolate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class WavReader {
	private static final int WAVE_HEADER_SIZE = 44;
	public static final int SAMPLE_RATE = 44100;
	private static final int BYTES_PER_SAMPLE = 2; // 16-bit audio
	private static final int BITS_PER_SAMPLE = 16; // 16-bit audio
	private static final double MAX_16_BIT = Short.MAX_VALUE; // 32,767
	private static final int SAMPLE_BUFFER_SIZE = 4096;

	// public static void main(String[] args) {
	//
	// StdAudio mStdAudio = new StdAudio();
	// double[] aa = null;
	// try {
	// aa = readByteData();
	// } catch (IOException e) {
	// // TODO Auto-generated catch block
	// e.printStackTrace();
	// }
	// return;
	// }

	public double[] read(String filePath) throws IOException {
		File file = new File(filePath);
		FileInputStream fis = null;

		fis = new FileInputStream(file);

		// 헤더 읽음
		byte[] header = new byte[WAVE_HEADER_SIZE];
		fis.read(header, 0, WAVE_HEADER_SIZE);

		// for (int i = 0; i < 44; i++) {
		// System.out.print(String.format("%2x", (byte) header[i]) + " ");
		// }
		// System.out.println();

		int fileSize = 0;

		int h40 = header[WAVE_HEADER_SIZE - 4] & 0xff;
		int h41 = header[WAVE_HEADER_SIZE - 3] & 0xff;
		int h42 = header[WAVE_HEADER_SIZE - 2] & 0xff;
		int h43 = header[WAVE_HEADER_SIZE - 1] & 0xff;

		fileSize = fileSize | h40;
		fileSize = fileSize | (h41 << 8);
		fileSize = fileSize | (h42 << 16);
		fileSize = fileSize | (h43 << 24);

		// System.out.println(" chunk size :: " + fileSize + " bytes");

		byte[] dataChunks = new byte[fileSize];

		fis.read(dataChunks, 0, fileSize);

		fis.close();

		int N = dataChunks.length;
		double[] d = new double[N / 2];
		for (int i = 0; i < N / 2; i++) {
			d[i] = ((short) (((dataChunks[2 * i + 1] & 0xFF) << 8) + (dataChunks[2 * i] & 0xFF)))
					/ ((double) MAX_16_BIT);
		}

		return d;
	}

}
