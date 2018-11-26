package sample.Model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class Model {

    private Parse parse;
    private ReadFile fileReader;
    private Document currentDocument;
    private String text;
    private boolean insideText;
    private Indexer indexer;

    public Model() {
        parse = new Parse();
        indexer = new Indexer();
        fileReader = new ReadFile(this);
    }

    public void readFiles(String filesPath, String stopWordsPath) {
        long tStart = System.currentTimeMillis();
        parse.setStopWords(fileReader.readStopWords(stopWordsPath));
        fileReader.readFile(filesPath);
        System.out.println("--------------------------------------");
        System.out.println("-----------------Complete-------------");
        long tEnd = System.currentTimeMillis();
        long tDelta = tEnd - tStart;
        double elapsedSeconds = tDelta / 1000.0;
        System.out.println("Time it took: " + elapsedSeconds + " seconds");
    }

    void processFile(ArrayList<Document> documents) {
        for (Document document : documents) {
            parse.setCurrentDocumentTerms(currentDocument);
            parse.parseDocument(document);
        }
    }

    public void index (){
        indexer.addAllTerms(parse.getAllTerms(), "");
        parse.getAllTerms().clear();
    }

//    private void createDocuments(List<String> data) {
//        currentDocument = null;
//        text = "";
//        insideText = false;
//        String city;
//        for (String line : data) {
//            if (!line.equals("")) {
//                if (line.equals("</TEXT>")) {
//                    currentDocument.setContent(text);
//                    insideText = false;
//                } else if (insideText) {
//                    text += line;
//                } else if (line.equals("<DOC>")) {
//                    currentDocument = new Document();
//                } else if (line.equals("</DOC>")) {
//                    documents.add(currentDocument);
//                    text = "";
//                } else if (line.equals("<TEXT>")) {
//                    insideText = true;
//                } else if (line.contains("<TI>")) {
//                    currentDocument.setTitle(removeTag(line));
//                } else if (line.contains("<DATE")) {
//                    currentDocument.setDate(removeTag(line));
//                } else if (line.contains("<DOCNO>")) {
//                    currentDocument.setId(removeTag(line));
//                } else if (line.contains("<F P=104>")) {
//                    city = removeTag(line);
//                    int position;
//                    for (position = 0; position < city.length(); position++) {
//                        if (city.charAt(position) != ' ')
//                            break;
//                    }
//                    city = city.substring(position);
//                    if (city.contains(" "))
//                        city = city.substring(0, city.indexOf(" "));
//                    currentDocument.setCity(city.toUpperCase());
//                }
//            }
//
//        }
//    }

    private String removeTag(String line) {
        return line.replaceAll("<.*?>", "");
    }

    //public ArrayList<Document> getDocuments() {return documents;}

}