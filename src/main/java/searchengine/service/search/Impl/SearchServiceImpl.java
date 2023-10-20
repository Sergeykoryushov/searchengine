package searchengine.service.search.Impl;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import searchengine.dto.SearchData;
import searchengine.dto.response.SearchResponse;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SearchIndex;
import searchengine.model.SiteForIndexing;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SearchIndexRepository;
import searchengine.repository.SiteRepository;
import searchengine.service.indexing.Impl.StartIndexingServiceImpl;
import searchengine.service.search.SearchService;

import javax.transaction.Transactional;
import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private final List<Integer> resultQueryFromPagesId = new ArrayList<>();


    @Override
    @Transactional
    public SearchResponse search(String query, int offset, int limit, String site) throws IOException {
        LemmaSearcherImpl lemmaSearcher = new LemmaSearcherImpl(pageRepository, lemmaRepository, searchIndexRepository);
        HashMap<String, Integer> lemmasCount = lemmaSearcher.gettingLemmasAndCountInText(query);
        Set<String> queryLemmas = lemmasCount.keySet();
        List<Integer> allSitesId = getAllSitesIdFromConfig(site);
        List<Lemma> lemmas = new ArrayList<>();
        for (Integer siteId : allSitesId) {
            List<Lemma> lemmasForOneSite = getLemmasFromRepository(queryLemmas, siteId);
            if (!lemmasForOneSite.isEmpty()) {
                lemmas.addAll(lemmasForOneSite);
                getResultQueryFromPagesId(lemmasForOneSite);
            }

        }

        if (!lemmas.isEmpty()) {
            lemmas.sort(Comparator.comparingInt(Lemma::getFrequency));
        }
        SearchResponse response = new SearchResponse();
        if (resultQueryFromPagesId.isEmpty()) {
            response.setResult(true);
            response.setCount(0);
            return response;
        }
        HashMap<Page, Float> sortedRelativeRelevance = getSortedRelativeRelevance(resultQueryFromPagesId, lemmas);
        long start1 = System.currentTimeMillis();
        List<SearchData> data = createSearchData(sortedRelativeRelevance, query, offset, limit);
        long finish1 = System.currentTimeMillis();
        System.out.println("Метод createSearchData выполнялся: " + (finish1 - start1) / 1000 + " сек.");
        response.setResult(true);
        response.setCount(sortedRelativeRelevance.size());
        response.setData(data);
        resultQueryFromPagesId.clear();
        return response;
    }

    public void getResultQueryFromPagesId(List<Lemma> lemmas) {
        List<Integer> pages = new ArrayList<>();
        for (Lemma lemma : lemmas) {
            List<Integer> pagesForLemma = searchIndexRepository.findPageIdsByLemmaId(lemma.getId());
            if (pagesForLemma.isEmpty()) {
                break;
            }
            if (pages.isEmpty()) {
                pages.addAll(pagesForLemma);
                continue;
            }
            pages.retainAll(pagesForLemma);
            if (pages.isEmpty()) {
                break;
            }
        }
        resultQueryFromPagesId.addAll(pages);
    }


    public List<Lemma> getLemmasFromRepository(Set<String> queryLemmas, int siteId) {
        List<Lemma> lemmas = new ArrayList<>();
        for (String lemma : queryLemmas) {
            Lemma lemmaFromRepository = lemmaRepository.findByLemmaAndSiteId(lemma, siteId);
            if (lemmaFromRepository == null) {
                lemmas.clear();
                return lemmas;
            }
            lemmas.add(lemmaFromRepository);
        }
        if (!lemmas.isEmpty()) {
            lemmas.sort(Comparator.comparingInt(Lemma::getFrequency));
        }
        return lemmas;
    }


    public HashMap<Page, Float> getSortedRelativeRelevance(List<Integer> resultQueryFromPagesId, List<Lemma> lemmaList) {
        HashMap<Page, Float> pagesRelativeRelevance = new HashMap<>();
        HashMap<Page, Float> pagesAbsolutRelevance = getAbsolutRelevance(resultQueryFromPagesId, lemmaList);
        Float maxAbsolutRelevance = Collections.max(pagesAbsolutRelevance.values());
        for (Map.Entry<Page, Float> entry : pagesAbsolutRelevance.entrySet()) {
            Float absolutRelevance = entry.getValue();
            Page page = entry.getKey();
            Float relativeRelevance = absolutRelevance / maxAbsolutRelevance;
            pagesRelativeRelevance.put(page, relativeRelevance);
        }
        HashMap<Page, Float> sortedRelativeRelevance = new LinkedHashMap<>();
        pagesRelativeRelevance.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
                .forEach(entry -> sortedRelativeRelevance.put(entry.getKey(), entry.getValue()));
        return sortedRelativeRelevance;
    }


    public HashMap<Page, Float> getAbsolutRelevance(List<Integer> resultQueryFromPagesId, List<Lemma> lemmas) {
        HashMap<Page, Float> pagesAbsolutRelevance = new HashMap<>();
        List<Integer> lemmasId = new ArrayList<>();
        for (Lemma lemma : lemmas) {
            lemmasId.add(lemma.getId());
        }
        List<Page> pages = pageRepository.findByIdIn(resultQueryFromPagesId);
        for (Page page : pages) {
            float absolutRelevance = 0;
            List<SearchIndex> searchIndexes = searchIndexRepository.findByLemmaIdInAndPageId(lemmasId, page.getId());
            for (SearchIndex searchIndex : searchIndexes) {
                absolutRelevance += searchIndex.getRank();
            }
            pagesAbsolutRelevance.put(page, absolutRelevance);
        }
        return pagesAbsolutRelevance;
    }


    public String deleteSlashToEnd(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }


    public String getHtmlTitle(String html) {
        Document document = Jsoup.parse(html);
        Element titleElement = document.select("title").first();
        return titleElement != null ? titleElement.text() : "No Title Found";
    }

    public String getSnippet(String text, String query) throws IOException {
        String russianText = getRussianText(text);
        String[] russianWords = russianText.split(" ");
        String[] queryWords = query.split(" ");
        List<String> queryWordsInText = getQueryWordsInText(russianWords, queryWords);
        StringBuilder snippetBuilder = new StringBuilder();
        for (String queryWord : queryWordsInText) {
            int contextLength = 40;
            int index = text.toLowerCase().indexOf(queryWord.toLowerCase());
            if (index >= 0) {
                int start = Math.max(0, index - contextLength);
                int end = Math.min(text.length(), index + queryWord.length() + contextLength);
                String context = text.substring(start, end);
                String snippet = context.replaceFirst(queryWord, "<b>" + queryWord + "</b>");
                snippetBuilder.append(snippet);
                snippetBuilder.append(" ... ");
            }
        }
        return snippetBuilder.toString();
    }

    public List<String> getQueryWordsInText(String[] russianWords, String[] queryWords) throws IOException {
        List<String> queryWordsInText = new ArrayList<>();
        LuceneMorphology luceneMorph = null;
        luceneMorph = new RussianLuceneMorphology();
        Map<String, Boolean> matchesWords = getMatchesWordsFromQuery(queryWords);
        for (int i = 0; i < russianWords.length; i++) {
            for (int k = 0; k < queryWords.length; k++) {
                if (matchesWords.get(queryWords[k])) {
                    continue;
                }
                List<String> queryLemmas = luceneMorph.getNormalForms(queryWords[k].toLowerCase());
                LuceneMorphology finalLuceneMorph = luceneMorph;
                int finalI = i;
                if (russianWords[i].isEmpty() || queryLemmas.stream().noneMatch(queryLemma -> {
                    List<String> wordBaseForms = finalLuceneMorph.getNormalForms(russianWords[finalI].toLowerCase());
                    return wordBaseForms.contains(queryLemma);
                })) {
                    continue;
                }
                queryWordsInText.add(russianWords[i]);
                matchesWords.put(queryWords[k], true);
            }
        }
        return queryWordsInText;
    }


    public Map<String, Boolean> getMatchesWordsFromQuery(String[] queryWords) {
        Map<String, Boolean> matchesWordsFromQuery = new HashMap<>();
        for (int i = 0; i < queryWords.length; i++) {
            matchesWordsFromQuery.put(queryWords[i], false);
        }
        return matchesWordsFromQuery;
    }


    public String getRussianText(String text) {
        LemmaSearcherImpl search = new LemmaSearcherImpl();
        text = search.removeHtmlTags(text);
        String[] russianWords = text.split(LemmaSearcherImpl.regexForSplitText);

        return String.join(" ", russianWords);
    }


    public List<Integer> getAllSitesIdFromConfig(String site) {
        List<Integer> allSitesId = new ArrayList<>();
        if (site == null) {
            List<SiteForIndexing> sites = siteRepository.findAll();
            for (SiteForIndexing siteForIndexing : sites) {
                allSitesId.add(siteForIndexing.getId());
            }
            return allSitesId;
        }
        SiteForIndexing siteForIndexing = siteRepository.findByUrl(StartIndexingServiceImpl.addSlashToEnd(site));
        if (siteForIndexing != null) {
            int siteId = siteForIndexing.getId();
            allSitesId.add(siteId);
        }
        return allSitesId;
    }

    private List<SearchData> createSearchData(HashMap<Page, Float> sortedRelativeRelevance, String query, int offset, int limit) throws IOException {
        int count = 0;
        List<SearchData> data = new ArrayList<>();
        for (Map.Entry<Page, Float> entry : sortedRelativeRelevance.entrySet()) {
            if (count < offset) {
                count++;
                continue;
            }
            if (count - offset > limit) {
                break;
            }

            Float relativeRelevance = entry.getValue();
            Page page = entry.getKey();
            String russianText = getRussianText(page.getContent());
            String snippet = getSnippet(russianText, query);
            if (snippet.isEmpty()) {
                continue;
            }
            SearchData searchData = SearchData.builder()
                    .site(deleteSlashToEnd(page.getSite().getUrl()))
                    .siteName(page.getSite().getName())
                    .uri(page.getPath())
                    .title(getHtmlTitle(page.getContent()))
                    .snippet(snippet)
                    .relevance(relativeRelevance)
                    .build();
            data.add(searchData);
            count++;
        }
        return data;
    }
}
