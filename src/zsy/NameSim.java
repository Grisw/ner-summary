package zsy;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NameSim extends UnicastRemoteObject implements INameSim {
	
	private Map<String, List<String>> entityToPronouns = new HashMap<>();
	private AbstractSequenceClassifier<CoreLabel> classifier;

	public NameSim(String serializedClassifier) throws IOException {
        try {
            classifier = CRFClassifier.getClassifier(serializedClassifier);
        } catch (ClassCastException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        initMap();
    }

	public String handleText(String text) throws RemoteException {
        BufferedReader reader = new BufferedReader(new StringReader(text));
        int count=0;
        //文档的核心词组，也就是第一句话的主语
        String keyWords = "";
        List<String> specialWord;
        //文档的核心词组所属的类别,如university、country、people等
        String keyWordsEntityClass;
        //文档的核心词组所属的类别具有的代换词，如university有List："It", "The university","The college", "The school", "The institution", "it","the university", "the college", "the school","the institution"
        List<String> pronouns;
        //文档的核心词组所属的类别具有的代换词，再加上该文档特有的代换词，如The University of Cambridge有List：上述pronouns列表加上"Cambridge"等缩写的专有名词。
        //如果是People的话，如Wolfgang Amadeus Mozart有List：key为people的pronouns列表加上"Mozart"等缩写的专有名词
        List<String> pronounsAdded = new ArrayList<>();
        //自定义二元组Tuple(String subject, List<String> description)，description中是openIE提取后的第二、第三元祖的连接的集合
        List<Tuple> tuples = new ArrayList<>();

        String line;
        try {
            while((line=reader.readLine())!=null){
                if (line.contains("||")) {
//            		System.out.println(line);
                    String r="\\t\\|\\|\\t";
                    String [] strings=line.split(r);
                    String t0=strings[0];
                    String t1=strings[2];
                    if(t1.contains("(")&&!t1.contains(")")){
                        t1=t1.substring(0,t1.indexOf("(")-1);
                    }
                    String t2=strings[3];
                    String t3="";
                    if(strings.length==5)
                        t3=strings[4];
    //        		System.out.println(t1+" | "+t2+" | "+t3);

                    //如果是第一个含有"||"的行
                    if (count == 0) {
                        keyWords = t1;
                        /*
                         * modify 2017-04-29
                         * 用了ner
                         * 输出为标签标注，用正则表达式提取</ >中的内容
                         * 暂时不考虑special word了吧，不过pronounsAdded还是保留了可作为后期需要拓展
                         */
                        String keyWordsEntityClassXml=classifier.classifyWithInlineXML(keyWords);
    //                    System.out.println("keyWords:"+keyWords);
    //                    System.out.println("keyWordsEntityClassXml:"+keyWordsEntityClassXml);
                        Pattern pattern = Pattern.compile("(?<=</).+?(?=>)");
                        Matcher matcher = pattern.matcher(keyWordsEntityClassXml);
                        if(matcher.find())
                            keyWordsEntityClass=matcher.group();
                        else
                            keyWordsEntityClass="OTHER";
                        //keyWordsEntityClass=classifier.classifyToString(keyWords, "tsv", false);
    //                    System.out.println(keyWordsEntityClass);
                        /*
                         * modify 2017-04-29
                         */
    //					specialWord="Cambridge";// getSpecialWordsFromKeyWordsAndFirstSent(String keyWords,String firstSent);未实现，暂定为Combridge
                        specialWord=getSpecialWordsFromKeyWordsAndClass(keyWords,keyWordsEntityClass);
                        pronouns = entityToPronouns.get(keyWordsEntityClass);
                        pronounsAdded.addAll(pronouns);
                        pronounsAdded.add(keyWords);
                        pronounsAdded.addAll(specialWord);

                        List<String> description = Collections.singletonList(t2 + " " + t3);
                        Tuple tuple = new Tuple(t1, description);
                        tuples.add(tuple);
                    } else if(Double.valueOf(t0)>0.80){
    //					System.out.println(Double.valueOf(t0));
                        //如果该句的主语是代换词，则转换为keyWords
    //					if (pronounsAdded.contains(t1)) {
                        if (canBeReplaced(pronounsAdded,keyWords,t1)) {
                            t1 = keyWords;
                        }
                        if (!tuples.isEmpty()) {
                            int tuplesSize = tuples.size();
                            //如果该句的主语与tuples列表中的最后一个tuple的主语一样，则则将该句的description即（t2+" "+t3）追加到上一个tuple的description列表中
                            if (t1.equals(tuples.get(tuplesSize - 1).getSubject())) {
                                Tuple lastTuple = tuples.remove(tuplesSize - 1);
                                List<String> lastTupleDescription = lastTuple.getDescription();
                                List<String> lastTupleDescription2 = new ArrayList<>();
                                lastTupleDescription2.addAll(lastTupleDescription);
                                String newstring=t2 + " " + t3;
                                lastTupleDescription2.add(newstring);
                                lastTuple.setDescription(lastTupleDescription2);
                                tuples.add(lastTuple);
                            }
                            //如果该句的主语与tuples列表中的最后一个tuple的主语不同，重新在tuples列表中添加一个新tuple
                            else {
                                List<String> description = Collections.singletonList(t2 + " " + t3);
                                Tuple tuple = new Tuple(t1, description);
                                tuples.add(tuple);
                            }
                        }
                    }
                    count++;
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        StringBuilder result=new StringBuilder();
        for(int i=0;i<tuples.size();i++){
            Tuple tuple=tuples.get(i);
            result.append("(").append(tuple.getSubject()).append("\n");
            result.append("[");
            List<String> descriptions=tuple.getDescription();
            for(int j=0;j<descriptions.size();j++){
                result.append(descriptions.get(j)).append("\n");
            }
            result.append("])\n");
        }
        return result.toString();
    }

	private void initMap(){
		String entity0 = "OTHER";
		List<String> pronouns0;
		pronouns0=Arrays.asList("");
		entityToPronouns.put(entity0, pronouns0);
		
		String entity1 = "ORGANIZATION";
		List<String> pronouns1;
		pronouns1=Arrays.asList("It", "The university","The college", "The school", "The institution", "The institute","it","the university", "the college", "the school","the institution","the institute");
		entityToPronouns.put(entity1, pronouns1);
		
		String entity2 = "LOCATION";
		List<String> pronouns2;
		pronouns2=Arrays.asList("It", "The country","it","the country","The State");
		entityToPronouns.put(entity2, pronouns2);
		
		String entity3 = "PERSON";
		List<String> pronouns3;
		pronouns3=Arrays.asList("She", "He");
		entityToPronouns.put(entity3, pronouns3);
	}
	//未实现
	private List<String> getSpecialWordsFromKeyWordsAndClass(String keyWords, String keyWordsEntityClass){
		
		List<String> specialWords= new ArrayList<>();
		
		List<String> keyWordsList= new ArrayList<>();
		String keyWordsArray[]=keyWords.split(" ");
        keyWordsList.addAll(Arrays.asList(keyWordsArray));
		
		if(keyWordsEntityClass.equals("PERSON")){
			return keyWordsList;
		}
		if(keyWordsEntityClass.equals("ORGANIZATION")){
//			String keyWordsEntityClassXml=classifier.classifyWithInlineXML(keyWords);
//			System.out.println("keyWords:"+keyWords);
//			System.out.println("keyWordsEntityClassXml:"+keyWordsEntityClassXml);
//			Pattern pattern = Pattern.compile("(?<=\\</).+?(?=\\>)");
//	        Matcher matcher = pattern.matcher(keyWordsEntityClassXml);
//	        if(matcher.find())
//	        	keyWordsEntityClass=matcher.group();
//	        else 
//	        	keyWordsEntityClass="OTHER";
			
//			List<String> stopWords =new ArrayList<String>();
//			stopWords=Arrays.asList("the","of","university","college","institute","institution");
//			for (int i=0;i<keyWordsList.size();i++) {
//				String result=classifier.classifyToString(keyWordsList.get(i));
//				String resultClass=result.split("/")[1];
//				System.out.println("======================"+resultClass);
//				
//				if(resultClass.equals("ORGANIZATION")){
//					specialWords.add(keyWordsList.get(i));
//				}else if(resultClass.equals("LOCATION")){
//					
//					boolean hasOtherWordOutOfStopList=false;
//					for(int j=0;j<keyWordsList.size()&&j!=i;j++){
//						String result_=classifier.classifyToString(keyWordsList.get(j));
//						String resultClass_=result.split("/")[1];
//						if(!stopWords.contains(keyWordsList.get(j).toLowerCase()))
//							hasOtherWordOutOfStopList=true;
//					}
//					if(hasOtherWordOutOfStopList!=true)
//						specialWords.add(keyWordsList.get(i));
//				}
//			}
			return specialWords;
	        //keyWordsEntityClass=classifier.classifyToString(keyWords, "tsv", false);
//			System.out.println(keyWordsEntityClass);  
		
		}
		return specialWords;
	}
	private boolean canBeReplaced(List<String> pronounsAdded,String keyWords, String t1) {
		String t1LowerCase=t1.toLowerCase();
		
		for(int i=0;i<pronounsAdded.size();i++){

			if(SimilarUtil.sim(pronounsAdded.get(i).toLowerCase(), t1LowerCase)>0.8){
				String pronounsAddedWord=pronounsAdded.get(i).toLowerCase();
//				System.out.println("t1:"+t1LowerCase);
//				System.out.println("word in list"+i+":"+pronounsAddedWord);
//				System.out.println(SimilarUtil.sim(pronounsAddedWord, t1LowerCase));
				return true;
			}

		}

		
		return false;
	}

}

