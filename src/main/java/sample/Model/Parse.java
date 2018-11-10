package sample.Model;

import java.util.HashMap;
import java.util.HashSet;

class Parse {
    private Stemmer stemmer;
    private boolean doStemming = true; //@TODO Needs to be set by UI
    private HashSet<String> stopWords;
    private Model model;
    private DocumentTerms currentDocumentTerms;
    private HashMap<String, String> numbers;
    private HashSet<String> percents;
    private HashSet<String> money;
    private HashSet<String> date;
    private HashMap<String, Term> allTerms;

    Parse(Model model) {
        this.model = model;
        stemmer = new Stemmer();
        numbers = new HashMap<>();
        allTerms = new HashMap<>();
        initRules();
    }

    void parseDocument(Document document) {
        if (document.getContent() != null) {
            String[] tokens = document.getContent().split(" ");
            for (int i = 0; i < tokens.length; i++) {
                if (!isStopWord(tokens[i]) && tokens[i] != "") {
                    tokens[i] = cleanString(tokens[i]);
                    if (doStemming) {
                        stemmer.setTerm(tokens[i]);
                        stemmer.stem();
                        tokens[i] = stemmer.getTerm();
                    }
                }
            }
            String phrase = tokens[0];
            for (int i = 1; i < tokens.length; i++) {
                tokens[i] = parseNumbers(tokens[i]);

                if (numbers.containsKey(tokens[i])) {
                    phrase += numbers.get(tokens[i]);
                } else {
                    addTerm(phrase);
                    phrase = tokens[i];
                }
            }
            addTerm(phrase);
        }
    }

    private String parseNumbers(String token) {
        if (token.length() > 0 && Character.isDigit(token.charAt(0)) && Character.isDigit(token.charAt(token.length() - 1)) && token.length() > 3) {
            token = token.replaceAll(",", "");
            StringBuilder ans = new StringBuilder(token);
            switch (ans.length()) {
                case 4:
                case 7:
                case 10:
                    ans.insert(1, ".");
                    break;
                case 5:
                case 8:
                case 11:
                    ans.insert(2, ".");
                    break;
                case 6:
                case 9:
                case 12:
                    ans.insert(3, ".");
                    break;
            }
            if (ans.length() < 8)
                ans.insert(ans.length(), "K");
            else if (ans.length() < 11)
                ans.insert(ans.length(), "M");
            else
                ans.insert(ans.length(), "T");
            return ans.toString();
        }
        return token;
    }

    private void addTerm(String term) {
        if (!allTerms.containsKey(term)) {
            Term newTerm = new Term(term);
            allTerms.put(term, newTerm);
            currentDocumentTerms.addTermToText(newTerm);
        } else {
            allTerms.get(term).increaseAmount();
            currentDocumentTerms.addTermToText(allTerms.get(term));
        }

    }

    private boolean isStopWord(String word) {
        if (stopWords.contains(word))
            return true;
        return false;
    }

    private String cleanString(String str) {
        if (str.length() == 0)
            return "";
        int ascii = (int) str.charAt(0);
        while (!((ascii >= 48 && ascii <= 57) || (ascii >= 65 && ascii <= 90) ||
                (ascii >= 97 && ascii <= 122) || str.length() == 1)) {
            str = str.substring(1);
            ascii = (int) str.charAt(0);
        }
        ascii = (int) str.charAt(str.length() - 1);
        while (!((ascii >= 48 && ascii <= 57) || (ascii >= 65 && ascii <= 90) ||
                (ascii >= 97 && ascii <= 122) || str.length() == 1)) {
            str = str.substring(0, str.length() - 1);
            ascii = (int) str.charAt(str.length() - 1);
        }
        return str;
    }

    void setStopWords(HashSet<String> stopWords) {
        this.stopWords = stopWords;
    }

    void setCurrentDocumentTerms(DocumentTerms documentTerms) {
        currentDocumentTerms = documentTerms;
    }

    private void initRules() {
        numbers.put("Thousand", "K");
        numbers.put("Million", "M");
        numbers.put("Billion", "B");
        numbers.put("Trillion", "T");
    }
}