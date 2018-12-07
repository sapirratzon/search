package Model;

import java.util.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Model {

    private Parse parse;
    private ReadFile fileReader;
    private Indexer indexer;
    private Document document;
    StringBuilder lines;
    private CityChecker cityChecker;
    private String postingPathDestination;
    private int nomOfDocs = 11800;
    private HashSet<String> languages;
    private ArrayList<Document> documents;
    private HashSet<String> stopWords;
    private HashMap<String, ArrayList<Object>> termsDictionary;
    private HashMap<Integer, ArrayList<Object>> documentsDictionary;
    private HashMap<String, CityInfo> citiesDictionary;
    private boolean isStemming = false;
    private int documentsAmount;
    private int termsAmount;
    private double totalTime;

    public Model() {
        parse = new Parse(this);
        citiesDictionary = new HashMap<>();
        cityChecker = new CityChecker(Main.citiesUrl, citiesDictionary);
        fileReader = new ReadFile(this);
        documents = new ArrayList<>();
        languages = new HashSet<>();
        termsDictionary = new HashMap<>();
        documentsDictionary = new HashMap<>();
        indexer = new Indexer(this, parse.getAllTerms(), documents);
        document = new Document();
    }

    public void readFiles(String filesPath, String stopWordsPath, String postingpath) {
        indexer.initCurrentPostFile();
        resetDictionaries(false);
        document.initialize();
        postingPathDestination = postingpath;
        long tStart = System.currentTimeMillis();
        stopWords = fileReader.readStopWords(stopWordsPath);
        parse.setStopWords(stopWords);
        fileReader.readFile(filesPath, true);
        fileReader.readFile(filesPath, false);
        indexer.mergeAllPostFiles();
        termsAmount = termsDictionary.size();
        documentsAmount = documentsDictionary.size();
        writeTermsDictionary();
        writeDocsDictionary();
        writeCitiesDictionary();
        parse.getAllTerms().clear();
        System.out.println("--------------------------------------");
        System.out.println("-----------------Complete-------------");
        long tEnd = System.currentTimeMillis();
        long tDelta = tEnd - tStart;
        totalTime = tDelta / 1000.0;
    }

    public void processFile(String fileAsString) {
        String[] allDocuments = fileAsString.split("<DOC>");
        int length = allDocuments.length;
        String document;
        for (int d = 0; d < length; d++) {
            document = allDocuments[d];
            if (document.length() == 0 || document.equals(" ")) continue;
            Document currentDocument = new Document();
            nomOfDocs--;
            int startTagIndex = document.indexOf("<DOCNO>");
            int endTagIndex = document.indexOf("</DOCNO>");
            if (startTagIndex != -1 && endTagIndex != -1)
                currentDocument.setId(document.substring(startTagIndex + 7, endTagIndex));
            startTagIndex = document.indexOf("<TEXT>");
            endTagIndex = document.indexOf("</TEXT>");
            if (startTagIndex != -1 && endTagIndex != -1)
                currentDocument.setContent(document.substring(startTagIndex + 6, endTagIndex));
            startTagIndex = document.indexOf("<TI>");
            endTagIndex = document.indexOf("</TI>");
            if (startTagIndex != -1 && endTagIndex != -1)
                currentDocument.setTitle(document.substring(startTagIndex + 4, endTagIndex));
            startTagIndex = document.indexOf("<DATE>");
            endTagIndex = document.indexOf("</DATE>");
            if (startTagIndex != -1 && endTagIndex != -1)
                currentDocument.setDate(document.substring(startTagIndex + 7, endTagIndex));
            startTagIndex = document.indexOf("<F P=104>");
            endTagIndex = document.indexOf("</F>", startTagIndex);
            if (startTagIndex != -1 && endTagIndex != -1)
                currentDocument.setCity(getFirstWord(document.substring(startTagIndex + 9, endTagIndex).toUpperCase()));
            startTagIndex = document.indexOf("<F P=105>");
            endTagIndex = document.indexOf("</F>", startTagIndex);
            if (startTagIndex != -1 && endTagIndex != -1) {
                String ans = cleanString(document.substring(startTagIndex + 9, endTagIndex));
                if (ans.length() > 0)
                    languages.add(ans);
            }
            documents.add(currentDocument);
            if (nomOfDocs < 0) {
                int size = documents.size();
                for (int i = 0; i < size; i++)
                    parse.parseDocument(documents.get(i));
                index();
                nomOfDocs = 11800;
            }
        }
    }

    private void writeTermsDictionary() {
        Object[] sortedTerms = termsDictionary.keySet().toArray();
        Arrays.sort(sortedTerms);
        StringBuilder line = new StringBuilder();
        List<String> lines = new LinkedList<>();
        int size = sortedTerms.length;
        for (int i = 1; i < size; i++) {
            line.append("<");
            line.append(sortedTerms[i]);
            line.append(":");
            line.append(termsDictionary.get(sortedTerms[i]).get(0)).append(",").append(termsDictionary.get(sortedTerms[i]).get(1));
            lines.add(line.toString());
            line.setLength(0);
        }
        String path = postingPathDestination + "/termsDictionary.txt";
        if (isStemming)
            path = postingPathDestination + "/termsDictionaryWithStemming.txt";
        Path file = Paths.get(path);
        try {
            Files.write(file, lines, Charset.forName("UTF-8"));
        } catch (Exception e) {
            System.out.println("cannot write to dictionary");
        }
    }

    private void writeDocsDictionary() {
        Object[] sortedDocuments = documentsDictionary.keySet().toArray();
        Arrays.sort(sortedDocuments);
        StringBuilder line = new StringBuilder();
        List<String> lines = new LinkedList<>();
        int size = sortedDocuments.length;
        for (int i = 0; i < size; i++) {
            line.append("<");
            line.append(sortedDocuments[i]).append(":");
            line.append(documentsDictionary.get(sortedDocuments[i]).get(0));
            line.append(",");
            line.append(documentsDictionary.get(sortedDocuments[i]).get(1));
            line.append(",");
            line.append(documentsDictionary.get(sortedDocuments[i]).get(2));
            if (!documentsDictionary.get(sortedDocuments[i]).get(3).equals("")) {
                line.append(",");
                line.append(documentsDictionary.get(sortedDocuments[i]).get(3));
            }
            lines.add(line.toString());
            line.setLength(0);
        }
        String path = postingPathDestination + "/documentsDictionary.txt";
        if (isStemming)
            path = postingPathDestination + "/documentsDictionaryWithStemming.txt";
        Path file = Paths.get(path);
        try {
            Files.write(file, lines, Charset.forName("UTF-8"));
        } catch (Exception e) {
            System.out.println("cannot write to dictionary");
        }
    }

    private void writeCitiesDictionary() {
        Object[] sortedCities = citiesDictionary.keySet().toArray();
        Arrays.sort(sortedCities);
        StringBuilder dictionaryLine = new StringBuilder();
        List<String> dictionaryLines = new LinkedList<>();
        StringBuilder postingLine = new StringBuilder();
        List<String> postingLines = new LinkedList<>();
        StringBuilder value = new StringBuilder();
        int size = sortedCities.length;
        for (int i = 1; i < size; i++) {
            dictionaryLine.append("<");
            dictionaryLine.append(sortedCities[i]).append(":");
            dictionaryLine.append(citiesDictionary.get(sortedCities[i]));
            dictionaryLines.add(dictionaryLine.toString());
            dictionaryLine.setLength(0);


            value.setLength(0);
            if (citiesDictionary.get(sortedCities[i]).getCurrency() != null && citiesDictionary.get(sortedCities[i]).getLocationsInDocuments().size() == 0) {
                citiesDictionary.remove(sortedCities[i]);
                continue;
            } else {
                postingLine.append("<");
                postingLine.append(sortedCities[i]);
                postingLine.append(": ");
                Object [] locations = citiesDictionary.get(sortedCities[i]).getLocationsInDocuments().keySet().toArray();
                for (int l = 0; l < locations.length; l ++) {
                    postingLine.append(locations[l]);
                    postingLine.append("(");
                    postingLine.append(citiesDictionary.get(sortedCities[i]).getLocationsInDocuments().get(locations[l]));
                    postingLine.append(")");
                }
                postingLines.add(postingLine.toString());
                postingLine.setLength(0);
            }
        }
        String path = postingPathDestination + "/citiesDictionary.txt";
        if (isStemming)
            path = postingPathDestination + "/citiesDictionaryWithStemming.txt";
        Path dictionaryFile = Paths.get(path);
        try {
            Files.write(dictionaryFile, dictionaryLines, Charset.forName("UTF-8"));
        } catch (Exception e) {
            System.out.println("cannot write to dictionary");
        }
        path = postingPathDestination + "/postCities.txt";
        if (isStemming)
            path += "/postCitiesWithStemming.txt";
        Path postingFile = Paths.get(path);
        try {
            Files.write(postingFile, postingLines, Charset.forName("UTF-8"));
        } catch (Exception e) {
            System.out.println("cannot write to dictionary");
        }
    }

    public void loadTermsDictionary() {
        lines = new StringBuilder();
        String path = postingPathDestination + "/termsDictionary.txt";
        if (isStemming)
            path = postingPathDestination + "/termsDictionaryWithStemming.txt";
        File file = new File(path);
        try {
            lines = new StringBuilder();
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String term = line.substring(1, line.indexOf(":"));
                String[] info = line.substring(line.indexOf(":") + 1).split(",");
                ArrayList<Object> attributes = new ArrayList<>();
                attributes.add(0, info[0]);
                attributes.add(1, info[1]);
                termsDictionary.put(term, attributes);
                lines.append("<");
                lines.append(term);
                lines.append(": (");
                lines.append(info[0]);
                lines.append(")");
                lines.append("\n");
            }
        } catch (FileNotFoundException e) {
            System.out.println("Cannot open the terms dictionary");
        }
    }

    public void loadDocsDictionary() {
        String path = postingPathDestination + "/documentsDictionary.txt";
        if (isStemming)
            path = postingPathDestination + "/documentsDictionaryWithStemming.txt";
        File file = new File(path);
        try {
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                int docIndex = Integer.parseInt(line.substring(1, line.indexOf(":")));
                String[] info = line.substring(line.indexOf(":") + 1).split(",");
                ArrayList<Object> attributes = new ArrayList<>();
                attributes.add(0, info[0]);
                attributes.add(1, info[1]);
                if (info.length == 3)
                    attributes.add(2, info[2]);
                else
                    attributes.add(2, "");
                documentsDictionary.put(docIndex, attributes);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Cannot open the documents dictionary");
        }
    }

    public void loadCitiesDictionary() {
        String path = postingPathDestination + "/citiesDictionary.txt";
        if (isStemming)
            path = postingPathDestination + "/citiesDictionaryWithStemming.txt";
        File file = new File(path);
        try {
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] info = line.substring(line.indexOf(":") + 1).split(",");
                String city = line.substring(1, line.indexOf(":"));
                CityInfo cityInfo = new CityInfo(city);
                cityInfo.setCountryName(info[0]);
                cityInfo.setCurrency(info[1]);
                cityInfo.setPopulation(info[2]);
                citiesDictionary.put(city, cityInfo);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Cannot open the city dictionary");
        }
    }

    public void addCitiesToDictionary(String fileAsString) { // change to split by the tag itself
        String[] allDocuments = fileAsString.split("<DOC>");
        String city = "";
        int size = allDocuments.length;
        for (int i = 0; i < size; i++) {
            if (allDocuments[i].length() == 0 || document.equals(" ")) continue;
            int startTagIndex = allDocuments[i].indexOf("<F P=104>");
            int endTagIndex = allDocuments[i].indexOf("</F>", startTagIndex);
            if (startTagIndex != -1 && endTagIndex != -1)
                addCityToDictionary(allDocuments[i].substring(startTagIndex + 9, endTagIndex));
        }
    } // change to split by the tag itself

    private void addCityToDictionary(String city) {
        city = getFirstWord(city);
        if (city.length() > 1) {
            if (!citiesDictionary.containsKey(city)) {
                citiesDictionary.put(city, cityChecker.getCityInfo(city));
            }
        }
    }

    private void index() {
        indexer.addAllTerms(postingPathDestination);
        indexer.addAllDocuments();
        parse.getAllTerms().clear();
        documents.clear();
    }

    void finishReading() {
        int size = documents.size();
        for (int i = 0; i < size; i++)
            parse.parseDocument(documents.get(i));
        index();
    }

    public void resetDictionaries(boolean resetCities) {
        termsDictionary.clear();
        documentsDictionary.clear();
        if (resetCities)
            citiesDictionary.clear();
    }

    private String cleanString(String str) {
        if (str.length() == 0)
            return "";
        char current = str.charAt(0);
        while (!(Character.isLetter(current))) {
            if (str.length() == 1) {
                return "";
            } else {
                str = str.substring(1);
                current = str.charAt(0);
            }
        }
        current = str.charAt(str.length() - 1);
        while (!(Character.isLetter(current))) {
            if (str.length() == 1) {
                return "";
            } else {
                str = str.substring(0, str.length() - 1);
                current = str.charAt(str.length() - 1);
            }
        }
        return str;
    }

    public StringBuilder getLines (){
        return lines;
    }

    private String getFirstWord(String str) {
        String ans = "";
        if (str.contains(" ")) {
            int counter;
            for (counter = 0; counter < str.length(); counter++) {
                if (str.charAt(counter) != ' ')
                    break;
            }
            ans = str.substring(counter);
            ans = ans.substring(0, ans.indexOf(' '));
        }
        return ans;
    }

    public HashSet<String> getLanguages() {
        return languages;
    }

    public HashMap<String, ArrayList<Object>> getTermsDictionary() { return termsDictionary; }

    public HashMap<Integer, ArrayList<Object>> getDocsDictionary() {
        return documentsDictionary;
    }

    public HashMap<String, CityInfo> getCitiesDictionary() {
        return citiesDictionary;
    }

    public Integer getTotalDocuments() {
        return documentsAmount;
    }

    public Integer getTotalTerms() {
        return termsAmount;
    }

    public Double getTotalTime() {
        return totalTime;
    }

    public void setStemming(boolean selected) {
        isStemming = selected;
        indexer.setStemming(selected);
        parse.setStemming(selected);
    }
}