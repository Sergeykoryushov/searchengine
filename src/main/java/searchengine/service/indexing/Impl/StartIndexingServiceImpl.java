package searchengine.service.indexing.Impl;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesConfig;
import searchengine.dto.response.IndexingResponse;
import searchengine.exception.IndexingAlreadyRunningException;
import searchengine.model.*;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SearchIndexRepository;
import searchengine.repository.SiteRepository;
import searchengine.service.indexing.RecursiveLinkParser;
import searchengine.service.indexing.StartIndexingService;

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
    private final SitesConfig sites;
    private final List<SiteForIndexing> sitesForStartIndexing = new ArrayList<>();
    @Getter
    private static List<RecursiveLinkParser> tasks = new ArrayList<>();;
    @Getter
    private static CopyOnWriteArrayList<ForkJoinPool> threads = new CopyOnWriteArrayList<>();
    @Getter
    private static ForkJoinPool commonPool;
    @Getter
    private AtomicBoolean stopAllIndexing = new AtomicBoolean(false);

    @Override
    public IndexingResponse startIndex() throws IndexingAlreadyRunningException {

        if (!tasks.isEmpty()){
            throw new IndexingAlreadyRunningException();
        }
        deleteAllSitesInRepository();
        sitesForStartIndexing.clear();
        addSiteForIndexing();
        Runnable runnable = () -> {
            try {
                startIndexingAllSites();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
        IndexingResponse indexingResponse = new IndexingResponse();
        indexingResponse.setResult(true);
        return indexingResponse;
    }


    public void addSiteForIndexing() {
        List<SiteConfig> sitesFromConfig = sites.getSites();
        for (SiteConfig site : sitesFromConfig) {
            addSiteInSitesForStartIndexing(site);
        }
        siteRepository.saveAll(sitesForStartIndexing);
    }

    public void deleteAllSitesInRepository() {
        if (siteRepository.count() > 0) {
            siteRepository.deleteAll();
        }
    }

    public void startIndexingAllSites() throws InterruptedException {
        long start = System.currentTimeMillis();
        for (SiteForIndexing siteForIndexing : sitesForStartIndexing) {
            Set<String> paths = new CopyOnWriteArraySet<>();
            RecursiveLinkParser task = new RecursiveLinkParser(siteForIndexing, siteForIndexing.getUrl(), 0,
                    pageRepository, siteRepository, sites, lemmaRepository, searchIndexRepository, paths);
            tasks.add(task);
        }
        commonPool = new ForkJoinPool();
        for (RecursiveLinkParser task : tasks) {
            commonPool.submit(() -> {
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
            });
        }
        commonPool.shutdown();
            commonPool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        long finish = System.currentTimeMillis();
        System.out.println("Программа выполнялась: " + (finish - start) / 1000/60 + " мин.");
    }

    public void addSiteInSitesForStartIndexing(SiteConfig site) {
        SiteForIndexing newSiteForIndexing = SiteForIndexing.builder()
                .name(site.getName())
                .url(addSlashToEnd(site.getUrl()))
                .siteStatus(SiteStatus.INDEXING)
                .build();
        sitesForStartIndexing.add(newSiteForIndexing);
    }


    public static String addSlashToEnd(String url) {
        return !url.endsWith("/") ? url + "/" : url;
    }
}
