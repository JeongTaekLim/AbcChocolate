package AbcChocolate;

import java.io.IOException;

public class Demo {

	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
//		FeatureExtractor fe=new FeatureExtractor("2초에서_3초_사이.wav");
//		String s=fe.getSvmFeature(2);
//		System.out.println(s);
		
		ProductSVMTrainSet psts;
		
		String prefix = "C:\\Users\\JeongTaek\\workspace\\AbcChocolate\\트레이닝데이터셋\\볼륨3_pilot11_12_15_18_20\\";
		

		
		psts = new ProductSVMTrainSet(prefix + "지역1\\", "+1");
		psts.setSaveFilePath(prefix + "loc1_trainset.txt");
		psts.makeTrainSet();

		psts = new ProductSVMTrainSet(prefix + "지역2\\", "+1");
		psts.setSaveFilePath(prefix + "loc2_trainset.txt");
		psts.makeTrainSet();

		psts = new ProductSVMTrainSet(prefix + "지역3\\", "+1");
		psts.setSaveFilePath(prefix + "loc3_trainset.txt");
		psts.makeTrainSet();
		
		psts = new ProductSVMTrainSet(prefix + "지역4\\", "+1");
		psts.setSaveFilePath(prefix + "loc4_trainset.txt");
		psts.makeTrainSet();
		
		psts = new ProductSVMTrainSet(prefix + "지역5\\", "+1");
		psts.setSaveFilePath(prefix + "loc5_trainset.txt");
		psts.makeTrainSet();
	}

}
