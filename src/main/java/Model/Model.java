package Model;

import jdk.nashorn.internal.runtime.regexp.joni.SearchAlgorithm;
import sun.plugin2.os.windows.SECURITY_ATTRIBUTES;

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

/**
 * The model class
 */
public class Model {
    private static int queryIndex = 0;
    private Parse parse;
    private ReadFile fileReader;
    private Indexer indexer;
    private Document document;
    private CityChecker cityChecker;
    private String postingPathDestination;
    private int nomOfDocs = 8000;
    private HashSet<String> languages;
    private ArrayList<Document> documents;
    private ArrayList<QueryDocument> queriesDocuments;
    private HashSet<String> stopWords;
    private HashMap<String, Term> termsDictionary;
    private HashMap<Integer, ArrayList<String>> documentsDictionary;
    private HashMap<String, City> citiesDictionary;
    private boolean isStemming = false;
    private int documentsAmount;
    private int termsAmount;
    private double totalTime;
    private Searcher searcher;

    /**
     * The model default constructor
     */
    public Model() {
        parse = new Parse(this);
        citiesDictionary = new HashMap<>();
        cityChecker = new CityChecker(this, Main.citiesUrl, citiesDictionary);
        fileReader = new ReadFile(this);
        documents = new ArrayList<>();
        languages = new HashSet<>();
        termsDictionary = new HashMap<>();
        documentsDictionary = new HashMap<>();
        indexer = new Indexer(this, parse.getAllTerms(), documents);
        document = new Document();
        queriesDocuments = new ArrayList<QueryDocument>();

    }

    /**
     * The function that manages the processing of the corpus. Tells the read-file class to read the corpus,index them,
     * and write the dictionaries to the disk.
     *
     * @param filesPath     - Path to the corpus
     * @param stopWordsPath - Path to the stop words file
     * @param postingpath   - Path to save the posting files
     */
    public void readFiles(String filesPath, String stopWordsPath, String postingpath) {
        indexer.initCurrentPostFile();
        resetDictionaries(false);
        document.initialize();
        postingPathDestination = postingpath;
        long tStart = System.currentTimeMillis();
        stopWords = fileReader.readStopWords(stopWordsPath);
        parse.setStopWords(stopWords);
        fileReader.readCorpus(filesPath, true);
        fileReader.readCorpus(filesPath, false);
        indexer.mergeAllPostFiles();
        termsAmount = termsDictionary.size();
        documentsAmount = documentsDictionary.size();
        writeTermsDictionary();
        writeDocsDictionary();
        writeCitiesDictionary();
        long tEnd = System.currentTimeMillis();
        long tDelta = tEnd - tStart;
        totalTime = tDelta / 1000.0;
    }

    /**
     * Function which receives a file as a string, splits it into documents with the relevant data and sends them
     * to parsing.
     *
     * @param fileAsString - The file as a string file
     */
    void processFile(String fileAsString) {
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
                currentDocument.setId(getId(document.substring(startTagIndex + 7, endTagIndex)));
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
                currentDocument.setCity(cleanString(getFirstWord(document.substring(startTagIndex + 9, endTagIndex).toUpperCase())));
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
                nomOfDocs = 8000;
            }
        }
    }

    private String getId(String str) {
        if (str.length() == 0)
            return "";
        char current = str.charAt(0);
        while (!(Character.isLetter(current)) && !Character.isDigit(current)) {
            if (str.length() == 1) {
                return "";
            } else {
                str = str.substring(1);
                current = str.charAt(0);
            }
        }
        current = str.charAt(str.length() - 1);
        while (!(Character.isLetter(current)) && !Character.isDigit(current)) {
            if (str.length() == 1) {
                return "";
            } else {
                str = str.substring(0, str.length() - 1);
                current = str.charAt(str.length() - 1);
            }
        }
        return str;
    }

    void processQuery(String fileAsString) {
        String[] allQueries = fileAsString.split("<top>");
        QueryDocument currentQuery = new QueryDocument();
        String allQuery = "";
        for (int query = 0; query < allQueries.length; query++) {
            allQuery = allQueries[query];
            int startTagIndex = allQuery.indexOf("<top>");
            int endTagIndex = allQuery.indexOf("<title>");
            if (startTagIndex != -1 && endTagIndex != -1)
                currentQuery.setId(allQuery.substring(allQuery.indexOf(": "), endTagIndex));
            startTagIndex = endTagIndex;
            endTagIndex = allQuery.indexOf("<de");
            if (startTagIndex != -1 && endTagIndex != -1)
                currentQuery.setContent(allQuery.substring(startTagIndex + 8, endTagIndex));
            queriesDocuments.add(currentQuery);
        }
        findRelevantDocuments(queriesDocuments);
    }

    /**
     * create a queryDocument from the user query
     *
     * @param query - the query from the user or file
     */
    public void findRelevantDocuments(String query) {
        QueryDocument queryDocument = new QueryDocument(query);
        queryDocument.setId(Integer.toString(queryIndex));
        queryIndex++;
        queriesDocuments.add(queryDocument);
        findRelevantDocuments(queriesDocuments);
    }

    /**
     * find the 50 most relevant documents for one or more queries by ranking the documents
     *
     * @param queriesDocuments - one or more queries from the user or file respectively
     */
    private void findRelevantDocuments(ArrayList<QueryDocument> queriesDocuments) {
        searcher = new Searcher(this, documentsDictionary, citiesDictionary, queriesDocuments);
        for (int i = 0; i < queriesDocuments.size(); i++) {
            parse.getAllTerms().clear();
            parse.parseDocument(queriesDocuments.get(i));
            searcher.findRelevantDocs();
        }
    }

    /**
     * Writes the terms dictionary to the disk.
     */
    private void writeTermsDictionary() {
        Object[] sortedTerms = termsDictionary.keySet().toArray();
        Arrays.sort(sortedTerms);
        StringBuilder line = new StringBuilder();
        List<String> lines = new LinkedList<>();
        int size = sortedTerms.length;
        for (int i = 0; i < size; i++) {
            line.append("<");
            line.append(sortedTerms[i]);
            line.append(":");
            line.append(termsDictionary.get(sortedTerms[i]).getAmount());
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

    /**
     * Writes the documents dictionary to the disk
     */
//    private void writeDocsDictionary() {
//        Object[] sortedDocuments = documentsDictionary.keySet().toArray();
//        Arrays.sort(sortedDocuments);
//        StringBuilder line = new StringBuilder();
//        List<String> lines = new LinkedList<>();
//        int size = sortedDocuments.length;
//        for (int i = 0; i < size; i++) {
//            line.append("<");
//            line.append(sortedDocuments[i]).append(":");
//            line.append(documentsDictionary.get(sortedDocuments[i]).getMax_tf());
//            line.append(",");
//            line.append(documentsDictionary.get(sortedDocuments[i]).getId());
//            line.append(",");
//            line.append(documentsDictionary.get(sortedDocuments[i]).getTextTerms().size());
//            line.append(",");
//            line.append(documentsDictionary.get(sortedDocuments[i]).getLength());
//            if (!documentsDictionary.get(sortedDocuments[i]).getCity().equals("")) {
//                line.append(",");
//                line.append(documentsDictionary.get(sortedDocuments[i]).getCity());
//            }
//            lines.add(line.toString());
//            line.setLength(0);
//        }
//        String path = postingPathDestination + "/documentsDictionary.txt";
//        if (isStemming)
//            path = postingPathDestination + "/documentsDictionaryWithStemming.txt";
//        Path file = Paths.get(path);
//        try {
//            Files.write(file, lines, Charset.forName("UTF-8"));
//        } catch (Exception e) {
//            System.out.println("cannot write to dictionary");
//        }
//    }
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
            line.append(",");
            line.append(documentsDictionary.get(sortedDocuments[i]).get(3));
            if (!documentsDictionary.get(sortedDocuments[i]).get(4).equals("")) {
                line.append(",");
                line.append(documentsDictionary.get(sortedDocuments[i]).get(4));
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

    /**
     * Writes the cities dictionary to the disk
     */
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
                postingLine.append(sortedCities[i].toString().toUpperCase());
                postingLine.append(": ");
                Object[] locations = citiesDictionary.get(sortedCities[i]).getLocationsInDocuments().keySet().toArray();
                for (int l = 0; l < locations.length; l++) {
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

    /**
     * Loads the terms dictionary from the disk
     */
    public void loadTermsDictionary() {
        String path = postingPathDestination + "/termsDictionary.txt";
        if (isStemming)
            path = postingPathDestination + "/termsDictionaryWithStemming.txt";
        File file = new File(path);
        try {
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String term = line.substring(1, line.indexOf(":"));
                Term newTerm = new Term(term);
                newTerm.setAmount(Integer.parseInt(line.substring(line.lastIndexOf(":") + 1)));
                termsDictionary.put(term, newTerm);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Cannot open the terms dictionary");
        }
    }

    /**
     * Loads the documents dictionary from the disk.
     */
//    public void loadDocsDictionary() {
//        String path = postingPathDestination + "/documentsDictionary.txt";
//        if (isStemming)
//            path = postingPathDestination + "/documentsDictionaryWithStemming.txt";
//        File file = new File(path);
//        try {
//            Scanner scanner = new Scanner(file);
//            while (scanner.hasNextLine()) {
//                String line = scanner.nextLine();
//                int docIndex = Integer.parseInt(line.substring(1, line.indexOf(":")));
//                String[] info = line.substring(line.indexOf(":") + 1).split(",");
//                Document document = new Document();
//                document.setMax_tf(Integer.parseInt(info[0]));
//                document.setId(info[1]);
//                //document.setTextTerms(new HashMap<Term, Integer>(Integer.parseInt(info[2])));
//                document.setLength(Integer.parseInt(info[3]));
//                if (info.length == 5)
//                    document.setCity(info[4]);
//                documentsDictionary.put(docIndex, document);
//            }
//        } catch (FileNotFoundException e) {
//            System.out.println("Cannot open the documents dictionary");
//        }
//    }
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
                ArrayList<String> attributes = new ArrayList<>();
                attributes.add(0, info[0]);
                attributes.add(1, info[1]);
                attributes.add(2, info[2]);
                attributes.add(3, info[3]);
                if (info.length == 5)
                    attributes.add(4, info[4]);
                else
                    attributes.add(4, "");
                documentsDictionary.put(docIndex, attributes);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Cannot open the documents dictionary");
        }
    }

    /**
     * Loads the cities dictionary from the disk
     */
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
                City cityInfo = new City(city);
                if (info.length > 2) {
                    cityInfo.setCountryName(info[0]);
                    cityInfo.setCurrency(info[1]);
                    cityInfo.setPopulation(info[2]);
                }
                citiesDictionary.put(city, cityInfo);

            }
        } catch (FileNotFoundException e) {
            System.out.println("Cannot open the city dictionary");
        }
    }

    /**
     * This function recieves a file as a string, splits it into documents and puts the city into the cities dictionary.
     *
     * @param fileAsString - The file as a string
     */
    void addCitiesToDictionary(String fileAsString) { // change to split by the tag itself
        String[] allDocuments = fileAsString.split("<F P=104>");
        String city = "";
        int size = allDocuments.length;
        for (int i = 0; i < size; i++) {
            if (allDocuments[i].length() == 0 || document.equals(" ")) continue;
            int endTagIndex = allDocuments[i].indexOf("</F>");
            if (endTagIndex != -1) {
                addCityToDictionary(allDocuments[i].substring(0, endTagIndex));
            }
        }
    }

    /**
     * Adds a city to the dictionary
     *
     * @param city - The city's name
     */
    private void addCityToDictionary(String city) {
        city = getFirstWord(city);
        if (city.length() > 0 && Character.isLetter(city.charAt(0))) {
            if (!citiesDictionary.containsKey(city)) {
                citiesDictionary.put(city, cityChecker.getCityInfo(city));
            }
        }
    }

    /**
     * Tells the indexer to index all the terms and documents and clears them after
     */
    private void index() {
        indexer.addAllTerms(postingPathDestination);
        indexer.addAllDocumentsToDictionary();
        parse.getAllTerms().clear();
        documents.clear();
    }

    /**
     * Parsing and indexing the last iteratotion of the documents.
     */
    void finishReading() {
        int size = documents.size();
        for (int i = 0; i < size; i++)
            parse.parseDocument(documents.get(i));
        index();
    }

    /**
     * Resets the dictionaries
     *
     * @param resetCities - If to reset the cities dictionary too
     */
    public void resetDictionaries(boolean resetCities) {
        termsDictionary.clear();
        documentsDictionary.clear();
        if (resetCities)
            citiesDictionary.clear();
    }

    /**
     * Cleans a string from spaces
     *
     * @param str - The string to clean
     * @return - The clean string
     */
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

    /**
     * Returns the first word of a given string
     *
     * @param str - The given string
     * @return - The first word of the given string
     */
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

    /**
     * Returns the languages
     *
     * @return - The languages
     */
    public HashSet<String> getLanguages() {
        return languages;
    }

    /**
     * Returns the terms dictionary
     *
     * @return - The terms dictionary
     */
    public HashMap<String, Term> getTermsDictionary() {
        return termsDictionary;
    }

    /**
     * Returns the documents dictionary
     *
     * @return - The documents dictionary
     */
    HashMap<Integer, ArrayList<String>> getDocsDictionary() {
        return documentsDictionary;
    }

    /**
     * Returns the cities dictionary
     *
     * @return - The cities dictionary
     */
    public HashMap<String, City> getCitiesDictionary() {
        return citiesDictionary;
    }

    /**
     * Returns the total amount of documents
     *
     * @return - THe amount of documetns
     */
    public Integer getTotalDocuments() {
        return documentsAmount;
    }

    /**
     * Returns the total amount of terms
     *
     * @return - The amount of terms
     */
    public Integer getTotalTerms() {
        return termsAmount;
    }

    /**
     * Returns the total amount of time it took to parse and index the corpus
     *
     * @return - The total time it took
     */
    public Double getTotalTime() {
        return totalTime;
    }

    /**
     * Tells if stemming is done
     *
     * @param selected - If stemming is done
     */
    public void setStemming(boolean selected) {
        isStemming = selected;
        indexer.setStemming(selected);
        parse.setStemming(selected);
    }

    /**
     * update the posting path
     *
     * @param postingPath - the path that contains all the posting files and dictionaries
     */
    public void setPostingPathDestination(String postingPath) {
        postingPathDestination = postingPath;
        fileReader.setPostPath(postingPath);
    }

    /**
     * @param terms - ArrayList of the terms from the query
     * @return ArrayList with the line of each term
     */
    public ArrayList<String> findTermFromPosting(ArrayList<String> terms) {
        return fileReader.findTerms(terms);
    }

    public ArrayList<Document> getQueryDocuments() {
        print();
        return sortDocuments(queriesDocuments.get(0).getQueryDocuments());
    }

    private ArrayList<Document> sortDocuments(PriorityQueue<Document> queryDocuments) {
        Comparator<Document> comparator = new Comparator<Document>() {
            @Override
            public int compare(Document o1, Document o2) {
                if (o1.getRank() > o2.getRank())
                    return -1;
                else if (o1.getRank() < o2.getRank())
                    return 1;
                return 0;
            }
        };
        ArrayList<Document> list = new ArrayList<>(queryDocuments);
        list.sort(comparator);
        return list;
    }


    private void print() {
        ArrayList<Document> list = sortDocuments(queriesDocuments.get(0).getQueryDocuments());
        for (int i = 0; i < 50 && i < list.size(); i++) {
            System.out.println("Doc: " + list.get(i).getId() + " rank: " + list.get(i).getRank());
        }
    }

    public HashMap<Integer, ArrayList<String>> getDocumentsDictionary() {
        return documentsDictionary;
    }
}