package AbcChocolate;

import java.io.IOException;

public class Demo {

	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
//		FeatureExtractor fe=new FeatureExtractor("2�ʿ���_3��_����.wav");
//		String s=fe.getSvmFeature(2);
//		System.out.println(s);
		
		ProductSVMTrainSet psts;
		

		
		psts = new ProductSVMTrainSet("C:\\Users\\JeongTaek\\workspace\\AbcChocolate\\Ʈ���̴׵����ͼ�\\����1\\", "+1");
		psts.setSaveFilePath("C:\\Users\\JeongTaek\\workspace\\AbcChocolate\\Ʈ���̴׵����ͼ�\\loc1_trainset.txt");
		psts.makeTrainSet();

		psts = new ProductSVMTrainSet("C:\\Users\\JeongTaek\\workspace\\AbcChocolate\\Ʈ���̴׵����ͼ�\\����2\\", "+1");
		psts.setSaveFilePath("C:\\Users\\JeongTaek\\workspace\\AbcChocolate\\Ʈ���̴׵����ͼ�\\loc2_trainset.txt");
		psts.makeTrainSet();

		psts = new ProductSVMTrainSet("C:\\Users\\JeongTaek\\workspace\\AbcChocolate\\Ʈ���̴׵����ͼ�\\����3\\", "+1");
		psts.setSaveFilePath("C:\\Users\\JeongTaek\\workspace\\AbcChocolate\\Ʈ���̴׵����ͼ�\\loc3_trainset.txt");
		psts.makeTrainSet();
		
		psts = new ProductSVMTrainSet("C:\\Users\\JeongTaek\\workspace\\AbcChocolate\\Ʈ���̴׵����ͼ�\\����4\\", "+1");
		psts.setSaveFilePath("C:\\Users\\JeongTaek\\workspace\\AbcChocolate\\Ʈ���̴׵����ͼ�\\loc4_trainset.txt");
		psts.makeTrainSet();
		
		psts = new ProductSVMTrainSet("C:\\Users\\JeongTaek\\workspace\\AbcChocolate\\Ʈ���̴׵����ͼ�\\����5\\", "+1");
		psts.setSaveFilePath("C:\\Users\\JeongTaek\\workspace\\AbcChocolate\\Ʈ���̴׵����ͼ�\\loc5_trainset.txt");
		psts.makeTrainSet();
	}

}
