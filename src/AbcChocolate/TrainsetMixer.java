package AbcChocolate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

public class TrainsetMixer {
	// dirPath 예시 : "C:\\Users\\JeongTaek\\Desktop\\으악\\", newFileName 예시 : "new_trainset.txt"
	public void trainsetMix(String dirPath, String newFileName){
		ArrayList<File> fileList = getFileList(dirPath);
		File newFile = null;
		for (Iterator<File> iter = fileList.iterator(); iter.hasNext();) {
			File tempFile = iter.next();
			if (tempFile.isFile()) {
				String tempFileName = tempFile.getName();
				if (tempFileName.equalsIgnoreCase(newFileName) == true) {
					newFile = tempFile;
					iter.remove();
				} else {

				}
			}
		}

		appendToOthers(fileList, newFile);
		appendToOne(fileList, newFile);
	}
	
	private void appendToOne(ArrayList<File> fileList, File newFile) {
		try {
			FileWriter fw = new FileWriter(newFile, true);
			for (File tempFile : fileList) {
				if (tempFile.isFile()) {
					ArrayList<String> tempContent = file2stringList(tempFile);
					for (String s : tempContent) {
						if (s.contains("+")) {
							s = s.replaceAll("\\+", "-");
							fw.append(s);
							fw.append("\n");
							System.out.println("파일 :: " + newFile.getName() + ", 추가된 내용 :: " + s);
						}
					}
				}
			}
			fw.close();
		} catch (IOException e) {

		}

	}

	private void appendToOthers(ArrayList<File> fileList, File newFile) {
		ArrayList<String> newFileContent = file2stringList(newFile);
		for (File tempFile : fileList) {
			if (tempFile.isFile()) {
				String tempFileName = tempFile.getName();
				try {
					FileWriter fw = new FileWriter(tempFile, true);
					for (String tempString : newFileContent) {
						tempString = tempString.replaceAll("\\+", "-");
						fw.append(tempString);
						fw.append("\n");
						System.out.println("파일 :: " + tempFileName + ", 추가된 내용 :: " + tempString);
					}
					fw.close();
				} catch (IOException e) {

				}
			}
		}

	}

	// 파일 확장자를 얻어오는 메소드
	private String getFileType(String fileName) {
		int pos = fileName.lastIndexOf(".");
		String ext = fileName.substring(pos + 1);
		return ext;
	}

	// 폴더 내부 파일 리스트를 얻어오는 메소드
	private ArrayList<File> getFileList(String dirPath) {

		File dirFile = new File(dirPath);
		File[] tmepFileList = dirFile.listFiles();
		ArrayList<File> fileList = new ArrayList<File>();

		for (File tempFile : tmepFileList) {
			if (tempFile.isFile()) {
				String tempPath = tempFile.getParent();
				String tempFileName = tempFile.getName();
				if (getFileType(tempFileName).equalsIgnoreCase("txt") == true) {
					fileList.add(tempFile);
				}
				/*** Do something withd tempPath and temp FileName ^^; ***/
			}
		}
		return fileList;
	}

	private ArrayList<String> file2stringList(File f) {
		ArrayList<String> stringList = new ArrayList<String>();
		try {
			BufferedReader in = new BufferedReader(new FileReader(f));
			String s;

			while ((s = in.readLine()) != null) {
				stringList.add(s);
			}
			in.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return stringList;
	}

}
