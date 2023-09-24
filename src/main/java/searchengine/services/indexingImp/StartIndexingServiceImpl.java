package searchengine.services.indexingImp;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SiteProperty;
import searchengine.config.SitesListProperties;
import searchengine.dto.response.IndexingResponse;
import searchengine.model.*;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SearchIndexRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.RecursiveLinkParser;
import searchengine.services.indexing.StartIndexingService;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Data
public class StartIndexingServiceImpl implements StartIndexingService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private final SitesListProperties sites;
    private final List<SiteForIndexing> sitesForStartIndexingList = new ArrayList<>();
    @Getter
    private static List<RecursiveLinkParser> tasks;
    @Getter
    private static CopyOnWriteArrayList<ForkJoinPool> threadList = new CopyOnWriteArrayList<>();
    @Getter
    private static ForkJoinPool commonPool;
    @Getter
    private AtomicBoolean stopAllIndexing = new AtomicBoolean(false);

    @Override
    public IndexingResponse startIndex() {
        IndexingResponse indexingResponse = new IndexingResponse();
        if (commonPool != null){
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация уже запущена");
            return indexingResponse;
        }
        deleteAllSitesInRepository();
        sitesForStartIndexingList.clear();
        addSiteForIndexing();
        Runnable runnable = this::startIndexingAllSites;
        Thread thread = new Thread(runnable);
        thread.start();
        indexingResponse.setResult(true);
        return indexingResponse;
    }


    public void addSiteForIndexing() {
        List<SiteProperty> siteList = sites.getSites();
        for (SiteProperty site : siteList) {
            addSiteInSiteListForStartIndexing(site);
        }
        siteRepository.saveAll(sitesForStartIndexingList);
    }

    public void deleteAllSitesInRepository() {
        if (siteRepository.count() > 0) {
            siteRepository.deleteAll();
        }
    }

    public void startIndexingAllSites() {
        long start = System.currentTimeMillis();
        tasks = new ArrayList<>();
        for (SiteForIndexing siteForIndexing : sitesForStartIndexingList) {
            Set<String> pathSet = new CopyOnWriteArraySet<>();
            RecursiveLinkParser task = new RecursiveLinkParser(siteForIndexing, siteForIndexing.getUrl(), 0,
                    pageRepository, siteRepository, sites, lemmaRepository, searchIndexRepository, pathSet);
            tasks.add(task);
        }
        commonPool = new ForkJoinPool();

        for (RecursiveLinkParser task : tasks) {
            commonPool.submit(() -> {
                try {
                    task.invoke();
                    if (stopAllIndexing.get()) {
                        return null;
                    }
                    SiteForIndexing siteFromRepository = siteRepository.findByUrl(task.getSiteForIndexing().getUrl());
                    if (!siteFromRepository.getSiteStatus().equals(SiteStatus.INDEXING)) {
                        return null;
                    }
                    siteFromRepository.setSiteStatus(SiteStatus.INDEXED);
                    siteRepository.save(siteFromRepository);
                    task.join();
                    return null;
                } catch (Exception exception) {
                    exception.printStackTrace();
                    throw exception;
                }
            });
        }
        commonPool.shutdown();
        try {
            commonPool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long finish = System.currentTimeMillis();
        System.out.println("Программа выполнялась: " + (finish - start) / 1000/60 + " мин.");
    }

    public void addSiteInSiteListForStartIndexing(SiteProperty site) {
        SiteForIndexing newSiteForIndexing = new SiteForIndexing();
        newSiteForIndexing.setName(site.getName());
        newSiteForIndexing.setUrl(addSlashToEnd(site.getUrl()));
        newSiteForIndexing.setSiteStatus(SiteStatus.INDEXING);
        sitesForStartIndexingList.add(newSiteForIndexing);
    }


    public static String addSlashToEnd(String url) {
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url;
    }
}
