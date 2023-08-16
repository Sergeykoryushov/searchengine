package searchengine.dto.statistics;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class SearchForLemmas {
    private static final String regexForSplitText = "[^А-Яа-яёЁ]+";
    private static final String[] partsOfSpeechNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    public HashMap<String, Integer> gettingLemmasInText(String text) {
        HashMap<String, Integer> lemmasCountMap = new HashMap<>();
        String[] words = text.split(regexForSplitText);
        LuceneMorphology luceneMorph = null;
        try {
            luceneMorph = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (String word : words) {
            purificationFromOfficialPartsOfSpeech(luceneMorph, word, lemmasCountMap);
        }
        return lemmasCountMap;
    }

    public void purificationFromOfficialPartsOfSpeech(LuceneMorphology luceneMorph, String word, HashMap<String, Integer> lemmasCountMap) {
        List<String> morphInfo = luceneMorph.getMorphInfo(word.toLowerCase());
        if (morphInfo.stream().anyMatch(this::hasParticleProperty)) {
            return;
        }
        List<String> wordBaseForms = luceneMorph.getNormalForms(word.toLowerCase());
        wordBaseForms.forEach(lemma -> lemmasCountMap.put(lemma, lemmasCountMap.getOrDefault(lemma, 0) + 1));
    }


    private boolean hasParticleProperty(String wordBase) {
        for (String property : partsOfSpeechNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }
    public  String removeHtmlTags(String html) {
        Document doc = Jsoup.parse(html);
        return doc.text();
    }
}

