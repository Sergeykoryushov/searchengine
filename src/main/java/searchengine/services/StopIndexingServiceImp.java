package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.dto.statistics.ParsingLinks;
import searchengine.model.SiteForIndexing;
import searchengine.model.SiteStatus;
import searchengine.repository.SiteRepository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class StopIndexingServiceImp implements StopIndexingService{
    private final StartIndexingServiceImpl startIndexingServiceImpl;
    private final SiteRepository siteRepository;

    @Override
    public IndexingResponse stopIndex() {
        IndexingResponse indexingResponse = new IndexingResponse();
        if (StartIndexingServiceImpl.getCommonPool() == null) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация не запущена");
            return indexingResponse;
        }
        StartIndexingServiceImpl.getCommonPool().shutdownNow();
//        startIndexingServiceImpl.setStopAllIndexing(true);
//        List<ParsingLinks> parsingLinksList = ParsingLinks.getParsingTasks();
//        for (ParsingLinks parsingTask : parsingLinksList) {
//            parsingTask.interruptTask();
//        }
        try {
            StartIndexingServiceImpl.getCommonPool().awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        List<SiteForIndexing> sitesIndexingNowList = siteRepository.findAll();
        for (SiteForIndexing site : sitesIndexingNowList) {
            if(!site.getSiteStatus().equals(SiteStatus.INDEXING)){
                continue;
            }
            site.setSiteStatus(SiteStatus.FAILED);
            site.setLastError("Индексация остановлена пользователем");
            siteRepository.save(site);
        }
        indexingResponse.setResult(true);
        return indexingResponse;
    }
}
