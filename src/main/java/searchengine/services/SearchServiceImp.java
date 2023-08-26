package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.dto.statistics.SearchLemmas;
import searchengine.dto.statistics.SearchResponse;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SearchIndexRepository;
import searchengine.repository.SiteRepository;

import javax.persistence.criteria.CriteriaBuilder;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchServiceImp implements SearchService{
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;

    @Override
    public SearchResponse search(String query) {
        SearchLemmas searchLemmas = new SearchLemmas(pageRepository, siteRepository, lemmaRepository, searchIndexRepository);
        SearchResponse response = new SearchResponse();
        HashMap<String, Integer> lemmasCountMap = searchLemmas.gettingLemmasInText(query);
        Set<String> lemmaSet = lemmasCountMap.keySet();
        List<Lemma> lemmaList = new ArrayList<>();
        for (String lemma: lemmaSet) {
            Lemma lemmaFromRepository = lemmaRepository.findByLemma(lemma);
            if(lemmaFromRepository == null){
                continue;
            }
            lemmaList.add(lemmaFromRepository);
        }
        lemmaList.sort(Comparator.comparingInt(Lemma::getFrequency));
        List<Integer> resultQueryList = new ArrayList<>();
        getResultQueryList(resultQueryList, lemmaList);
        if(!resultQueryList.isEmpty()){
            for (Integer pageId: resultQueryList){
                Optional<Page> optionalPage = pageRepository.findById(pageId);
                if (optionalPage.isPresent()) {
                    Page page = optionalPage.get();
                }


            }



        }
        response.setResult(true);
        return response;
    }

    public void getResultQueryList(List<Integer> resultQueryList, List<Lemma> lemmaList) {
        for (Lemma lemma : lemmaList) {
            List<Integer> pagesForLemma = searchIndexRepository.findPageIdsByLemmaId(lemma.getId());
            if (pagesForLemma.isEmpty()) {
                break;
            }
            if (resultQueryList.isEmpty()) {
                resultQueryList.addAll(pagesForLemma);
                continue;
            }
            resultQueryList.retainAll(pagesForLemma);
            if (resultQueryList.isEmpty()) {
                break;
            }
        }
    }


}
