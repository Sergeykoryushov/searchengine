package searchengine.service.indexing.Impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.response.IndexingResponse;
import searchengine.exception.IndexingNotRunningException;
import searchengine.model.SiteForIndexing;
import searchengine.model.SiteStatus;
import searchengine.repository.SiteRepository;
import searchengine.service.indexing.RecursiveLinkParser;
import searchengine.service.indexing.StopIndexingService;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class StopIndexingServiceImpl implements StopIndexingService {
    private final StartIndexingServiceImpl startIndexingServiceImpl;
    private final SiteRepository siteRepository;

    @Override
    public IndexingResponse stopIndex() throws InterruptedException, IndexingNotRunningException {
        IndexingResponse indexingResponse = new IndexingResponse();
        if (StartIndexingServiceImpl.getCommonPool() == null) {
            throw new IndexingNotRunningException();
        }
        startIndexingServiceImpl.getStopAllIndexing().set(true);
        List<RecursiveLinkParser> recursiveLinkParserList = RecursiveLinkParser.getParsingTasks();
        for (RecursiveLinkParser parsingTask : recursiveLinkParserList) {
            parsingTask.interruptTask();
        }

        StartIndexingServiceImpl.getCommonPool().awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        List<SiteForIndexing> sitesIndexingNow = siteRepository.findAll();
        for (SiteForIndexing site : sitesIndexingNow) {
            if (!site.getSiteStatus().equals(SiteStatus.INDEXING)) {
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
