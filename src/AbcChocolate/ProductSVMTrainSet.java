package AbcChocolate;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class ProductSVMTrainSet {
	
	private File dir = null;
	private String result = "";
	private String label;
	private String saveFilePath;
	
	public ProductSVMTrainSet(String dirPath, String label) {
		this.dir = new File(dirPath);
		this.label = label;
		if( !dir.exists() || !dir.isDirectory() ) {
			throw new RuntimeException("No directory");
		}
	}
	
	public void makeTrainSet()  {
		
		File[] fileList = dir.listFiles();
		
		int cnt=0;
		Queue<Thread> threadList = new LinkedBlockingQueue<Thread>();
		
		for(File file : fileList) {
			
			
			/* Thread 시작하고 큐에 추가 */
			if( file.isFile()) {
				String fileName = file.getName();
				if( fileName.endsWith(".wav") ) {
					String fullPath = dir.getPath() + "\\" + fileName;
					Thread t = new DoExtract(fullPath);
					t.setPriority(Thread.MAX_PRIORITY);
					
					threadList.add(t);
					t.start();
				}
			}
			
			/* 큐의 크기가 10 이상이면 큐에서 꺼내면서 조인 시작 */
			if( threadList.size() >= 10) {
				while( !threadList.isEmpty()) {
					Thread t = threadList.poll();
					try {
						t.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		/* 큐에 남아있다면.. */
		while( !threadList.isEmpty() ) {
			Thread t = threadList.poll();
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		
		
		
		if( saveFilePath != null) {
			File sFile = new File(saveFilePath);
			try {
				PrintWriter pw = new PrintWriter(sFile);
				pw.write(result);
				pw.flush();
				pw.close();
			}
			catch(IOException e) {
				e.printStackTrace();
			}
		} else {
			throw new RuntimeException("NO_SAVE_FILE_PATH!!");
		}
		
	}
	
	
	public void setSaveFilePath(String path) {
		saveFilePath = path;
	}
	
	/* result에 스트링 이어붙임 */
	private synchronized void stringAppend(String data) {
		result += data + "\n";
	}
	
	
	
	private class DoExtract extends Thread {
		
		private String filePath;
		public DoExtract(String filePath) {
			this.filePath = filePath;
		}

		@Override
		public void run() {
			System.out.println(" Run thread :: " + filePath);
			FeatureExtractor2 fe = new FeatureExtractor2(filePath, label);
			try {
				String data = fe.getSvmFeature(2);
				stringAppend(data);
			}
			catch(IOException e) {
				e.printStackTrace();
			}
		}
		
		
		
	}
}
