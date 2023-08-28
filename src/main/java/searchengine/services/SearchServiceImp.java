package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.SearchData;
import searchengine.dto.statistics.SearchLemmas;
import searchengine.dto.statistics.SearchResponse;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SearchIndex;
import searchengine.model.SiteForIndexing;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SearchIndexRepository;
import searchengine.repository.SiteRepository;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchServiceImp implements SearchService{
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private final SitesList sites;

    @Override
    public SearchResponse search(String query, int offset, int limit, String site) {
//        int siteId = 0;
//        if(site != null){
//            SiteForIndexing siteForIndexing = siteRepository.findByUrl(site);
//            if(siteForIndexing != null) {
//                siteId = siteForIndexing.getId();
//            }
//        }
        SearchLemmas searchLemmas = new SearchLemmas(pageRepository, siteRepository, lemmaRepository, searchIndexRepository);
        SearchResponse response = new SearchResponse();
        HashMap<String, Integer> lemmasCountMap = searchLemmas.gettingLemmasInText(query);
        Set<String> queryLemmaSet = lemmasCountMap.keySet();
        List<Integer> allSitesId = getAllSitesIdFromConfig();
        List<Lemma> lemmaList = new ArrayList<>();
        for (Integer siteId: allSitesId ) {
             lemmaList = getSortedLemmaListFromRepository(queryLemmaSet, siteId);
        }
        List<Integer> resultQueryFromPagesIdList = getResultQueryFromPagesIdList(lemmaList);
        if(resultQueryFromPagesIdList.isEmpty()) {
            response.setResult(true);
            response.setCount(0);
            return response;
        }
        HashMap<Page, Float> sortedRelativeRelevanceMap = getSortedRelativeRelevanceMap(resultQueryFromPagesIdList, lemmaList);
        List<SearchData> data = new ArrayList<>();
        for (Map.Entry<Page, Float> entry : sortedRelativeRelevanceMap.entrySet()) {
            SearchData searchData = new SearchData();
            Float relativeRelevance = entry.getValue();
            Page page = entry.getKey();
            String russianText = getRussianText(page.getContent());
            String snippet = getSnippet(russianText,query);
            searchData.setSite(deleteSlashToEnd(page.getSite().getUrl()));
            searchData.setSiteName(page.getSite().getName());
            searchData.setUri(page.getPath());
            searchData.setTitle(getHtmlTitle(page.getContent()));
            searchData.setSnippet(snippet);
            searchData.setRelevance(relativeRelevance);
            data.add(searchData);
        }
        response.setResult(true);
        response.setCount(resultQueryFromPagesIdList.size());
        response.setData(data);
        return response;
    }

    public List<Integer> getResultQueryFromPagesIdList(List<Lemma> lemmaList) {
        List<Integer> resultQueryFromPagesIdList = new ArrayList<>();
        for (Lemma lemma : lemmaList) {
            List<Integer> pagesForLemma = searchIndexRepository.findPageIdsByLemmaId(lemma.getId());
            if (pagesForLemma.isEmpty()) {
                break;
            }
            if (resultQueryFromPagesIdList.isEmpty()) {
                resultQueryFromPagesIdList.addAll(pagesForLemma);
                continue;
            }
            resultQueryFromPagesIdList.retainAll(pagesForLemma);
            if (resultQueryFromPagesIdList.isEmpty()) {
                break;
            }
        }
        return resultQueryFromPagesIdList;
    }



    public List<Lemma> getSortedLemmaListFromRepository(Set<String> queryLemmaSet,int siteId){
        List<Lemma> lemmaList = new ArrayList<>();

        for (String lemma: queryLemmaSet) {
            Lemma lemmaFromRepository = null;
            if(siteId != 0) {
                lemmaFromRepository = lemmaRepository.findByLemmaAndSiteId(lemma,siteId);
                System.out.println(lemmaFromRepository.getId());
                System.out.println(lemmaFromRepository.getSite().getId());
            } else {
                lemmaFromRepository = lemmaRepository.findByLemma(lemma);
                System.out.println(lemmaFromRepository.getSite().getId());
            }

            if(lemmaFromRepository == null){
                continue;
            }
            lemmaList.add(lemmaFromRepository);
        }
        lemmaList.sort(Comparator.comparingInt(Lemma::getFrequency));
        return lemmaList;
    }



    public HashMap<Page, Float> getSortedRelativeRelevanceMap( List<Integer> resultQueryFromPagesIdList, List<Lemma> lemmaList){
        HashMap<Page,Float> pagesRelativeRelevanceMap = new HashMap<>();
        HashMap<Page,Float> pagesAbsolutRelevanceMap = getAbsolutRelevanceMap( resultQueryFromPagesIdList,lemmaList);
        Float maxAbsolutRelevance = Collections.max(pagesAbsolutRelevanceMap.values());
        for (Map.Entry<Page, Float> entry : pagesAbsolutRelevanceMap.entrySet()) {
            Float absolutRelevance = entry.getValue();
            Page page = entry.getKey();
            Float relativeRelevance = absolutRelevance/maxAbsolutRelevance;
            pagesRelativeRelevanceMap.put(page,relativeRelevance);
        }
        HashMap<Page, Float> sortedRelativeRelevanceMap = new HashMap<>();
        pagesRelativeRelevanceMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> sortedRelativeRelevanceMap.put(entry.getKey(), entry.getValue()));
        return sortedRelativeRelevanceMap;
    }



    public  HashMap<Page, Float> getAbsolutRelevanceMap( List<Integer> resultQueryFromPagesIdList, List<Lemma> lemmaList){
        HashMap<Page,Float> pagesAbsolutRelevanceMap = new HashMap<>();
        for (Integer pageId: resultQueryFromPagesIdList){
            float absolutRelevance = 0;
            Optional<Page> optionalPage = pageRepository.findById(pageId);
            if (optionalPage.isPresent()) {
                Page page = optionalPage.get();
                for (Lemma lemma : lemmaList){
                    SearchIndex searchIndex =  searchIndexRepository.findByLemmaIdAndPageId(lemma.getId(), pageId);
                    absolutRelevance +=searchIndex.getRank();
                }
                pagesAbsolutRelevanceMap.put(page,absolutRelevance);
            }
        }
        return pagesAbsolutRelevanceMap;
    }


    public String deleteSlashToEnd(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }


    public String getHtmlTitle(String html) {
        Document document = Jsoup.parse(html);
        Element titleElement = document.select("title").first();

        if (titleElement != null) {
            return titleElement.text();
        }
        return "No Title Found";
    }

    public String getSnippet(String text, String query) {
        int contextLength = 20;
        List<Integer> matchPositions = new ArrayList<>();
        int index = text.toLowerCase().indexOf(query.toLowerCase());
        while (index >= 0) {
            matchPositions.add(index);
            index = text.toLowerCase().indexOf(query.toLowerCase(), index + 1);
        }

        StringBuilder snippetBuilder = new StringBuilder();
        for (int position : matchPositions) {
            int start = Math.max(0, position - contextLength);
            int end = Math.min(text.length(), position + query.length() + contextLength);

            String context = text.substring(start, end);
            String snippet = context.replaceAll(query, "<b>" + query + "</b>");

            snippetBuilder.append(snippet);
            snippetBuilder.append(" ... ");
        }

        return snippetBuilder.toString();
    }

    public String getRussianText(String text){
        SearchLemmas search = new SearchLemmas();
        text = search.removeHtmlTags(text);
        String[] words = text.split(SearchLemmas.regexForSplitText);
        return String.join(" ", words);
    }
    public List<Integer> getAllSitesIdFromConfig(){
        List<Integer> allSitesId = new ArrayList<>();
        List<SiteForIndexing> siteList = siteRepository.findAll();
        for (SiteForIndexing site: siteList) {
            allSitesId.add(site.getId());
        }
        return allSitesId;
    }
}
