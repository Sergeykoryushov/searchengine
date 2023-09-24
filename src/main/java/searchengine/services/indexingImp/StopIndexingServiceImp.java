package searchengine.services.indexingImp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.response.IndexingResponse;
import searchengine.model.SiteForIndexing;
import searchengine.model.SiteStatus;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.RecursiveLinkParser;
import searchengine.services.indexing.StopIndexingService;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class StopIndexingServiceImp implements StopIndexingService {
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
        startIndexingServiceImpl.setStopAllIndexing(new AtomicBoolean(true));
        List<RecursiveLinkParser> recursiveLinkParserList = RecursiveLinkParser.getParsingTasks();
        for (RecursiveLinkParser parsingTask : recursiveLinkParserList) {
            parsingTask.interruptTask();
        }
        try {
            StartIndexingServiceImpl.getCommonPool().awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
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
        StartIndexingServiceImpl.getTasks().clear();
        indexingResponse.setResult(true);
        return indexingResponse;
    }
}
