package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.dto.statistics.ParsingLinks;
import searchengine.model.SiteForIndexing;
import searchengine.model.SiteStatus;
import searchengine.repository.SiteRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StopIndexingServiceImp implements StopIndexingService{
    private final StartIndexingServiceImpl startIndexingServiceImpl;
    private final SiteRepository siteRepository;

    @Override
    public IndexingResponse stopIndex() {
        IndexingResponse indexingResponse = new IndexingResponse();
        if (StartIndexingServiceImpl.getThreadList().isEmpty()) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация не запущена");
            return indexingResponse;
        }
        startIndexingServiceImpl.setStopAllIndexing(true);
        List<ParsingLinks> parsingLinksList = ParsingLinks.getParsingTasks();
        for (ParsingLinks parsingTask : parsingLinksList) {
            parsingTask.interruptTask();
        }
        ParsingLinks.getParsingTasks().clear();
        startIndexingServiceImpl.waitAllThreads();
        List<SiteForIndexing> sitesIndexingNowList = siteRepository.findAll();
        for (SiteForIndexing site : sitesIndexingNowList) {
            site.setSiteStatus(SiteStatus.FAILED);
            site.setLastError("Индексация остановлена пользователем");
            siteRepository.save(site);
        }
        indexingResponse.setResult(true);
        StartIndexingServiceImpl.getThreadList().clear();
        return indexingResponse;
    }
}
