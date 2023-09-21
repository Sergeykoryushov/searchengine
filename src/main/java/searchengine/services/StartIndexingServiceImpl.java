package searchengine.services;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.ParsingLinks;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.model.*;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SearchIndexRepository;
import searchengine.repository.SiteRepository;

import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Data
public class StartIndexingServiceImpl implements StartIndexingService{
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private final SitesList sites;
    private final List<SiteForIndexing> sitesForStartIndexingList = new ArrayList<>();
    @Getter
    private static List<ParsingLinks> tasks;
    @Getter
    private static CopyOnWriteArrayList<ForkJoinPool> threadList = new CopyOnWriteArrayList<>();
    @Getter
    private static ForkJoinPool commonPool;
    @Getter
    private boolean stopAllIndexing = false;

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
        List<Site> siteList = sites.getSites();
        for (Site site : siteList) {
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
            ParsingLinks task = new ParsingLinks(siteForIndexing, siteForIndexing.getUrl(), 0,
                    pageRepository, siteRepository, sites, lemmaRepository, searchIndexRepository, pathSet);
            tasks.add(task);
        }
        commonPool = new ForkJoinPool();

        for (ParsingLinks task : tasks) {
            commonPool.submit(() -> {
                try {
                    task.invoke();
                    if (stopAllIndexing) {
                        return null;
                    }
                    SiteForIndexing siteFromRepository = siteRepository.findByUrl(task.getSite().getUrl());
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
            throw new RuntimeException(e);
        }
        long finish = System.currentTimeMillis();
        System.out.println("Программа выполнялась: " + (finish - start) / 1000/60 + " мин.");
    }

    public void addSiteInSiteListForStartIndexing(Site site) {
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
