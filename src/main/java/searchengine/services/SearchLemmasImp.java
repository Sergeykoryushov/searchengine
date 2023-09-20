package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SearchIndex;
import searchengine.model.SiteForIndexing;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SearchIndexRepository;

import javax.transaction.Transactional;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
@Service
@RequiredArgsConstructor
public class SearchLemmasImp implements SearchLemmas{
    private PageRepository pageRepository;
    private LemmaRepository lemmaRepository;
    private SearchIndexRepository searchIndexRepository;
    public static final String regexForSplitText = "[^А-Яа-яёЁ]+";
    private static final String[] partsOfSpeechNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    public SearchLemmasImp(PageRepository pageRepository, LemmaRepository lemmaRepository, SearchIndexRepository searchIndexRepository) {
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.searchIndexRepository = searchIndexRepository;
    }


    public HashMap<String, Integer> gettingLemmasAndCountInText(String text) {
        text = removeHtmlTags(text);
        HashMap<String, Integer> lemmasCountMap = new HashMap<>();
        String[] russianWords = text.split(regexForSplitText);
        LuceneMorphology luceneMorph = null;
        try {
            luceneMorph = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(russianWords.length < 1){
            return lemmasCountMap;
        }
        for (String russianWord : russianWords) {
            purificationFromOfficialPartsOfSpeech(luceneMorph, russianWord, lemmasCountMap);
        }
        return lemmasCountMap;
    }

    public void purificationFromOfficialPartsOfSpeech(LuceneMorphology luceneMorph, String word, HashMap<String, Integer> lemmasCountMap) {
        if(word.equals("")){
            return;
        }
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
    public String removeHtmlTags(String html) {
        Document doc = Jsoup.parse(html);
        return doc.text();
    }
    @Transactional
    public void saveLemma(String path, SiteForIndexing siteForIndexing) {
        Page updatePage = pageRepository.findByPathAndSiteId(path, siteForIndexing.getId());
        String updatePageHtml = updatePage.getContent();
        HashMap<String, Integer> lemmasCountMap = gettingLemmasAndCountInText(updatePageHtml);
        Set<String> lemmasSet = lemmasCountMap.keySet();
        for (String lemmaForPage : lemmasSet) {
            Lemma lemma = lemmaRepository.findByLemmaAndSiteId(lemmaForPage,siteForIndexing.getId());
            if (lemma != null) {
                int frequency = lemma.getFrequency();
                lemma.setFrequency(frequency + 1);
                lemmaRepository.saveAndFlush(lemma);
                saveSearchIndexInSearchIndexRepository(lemmasCountMap, lemmaForPage, updatePage, siteForIndexing);
                continue;
            }
            Lemma newLemma = new Lemma();
            newLemma.setFrequency(1);
            newLemma.setLemma(lemmaForPage);
            newLemma.setSite(siteForIndexing);
            lemmaRepository.saveAndFlush(newLemma);
            saveSearchIndexInSearchIndexRepository(lemmasCountMap,lemmaForPage,updatePage, siteForIndexing);
        }
    }

    public void saveSearchIndexInSearchIndexRepository
            (HashMap<String, Integer> lemmasCountMap, String lemmaForPage, Page updatePage, SiteForIndexing siteForIndexing) {
        SearchIndex searchIndex = new SearchIndex();
        Lemma lemmaRepositoryByLemma = lemmaRepository.findByLemmaAndSiteId(lemmaForPage,siteForIndexing.getId());
        float rank = lemmasCountMap.get(lemmaForPage);
        searchIndex.setLemma(lemmaRepositoryByLemma);
        searchIndex.setPage(updatePage);
        searchIndex.setRank(rank);
        searchIndexRepository.saveAndFlush(searchIndex);
    }

}

