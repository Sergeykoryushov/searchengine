package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.ParsingLinks;
import searchengine.dto.statistics.ResultForIndexing;
import searchengine.model.SiteForIndexing;
import searchengine.model.SiteStatus;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class StartIndexingServiceImp implements StartIndexingService{
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SitesList sites;
    private  final List<SiteForIndexing> sitesForStartIndexingList = new ArrayList<>();
    private final List<ResultForIndexing> resultForIndexingList = new ArrayList<>();
    private static ForkJoinPool pool = new ForkJoinPool();

    @Override
    public List<ResultForIndexing> startIndex() {
        deleteSitesAllData();
        addSite();
        startIndexingAllSites();
        return resultForIndexingList;
    }

    @Override
    public List<ResultForIndexing> stopIndex() {
        resultForIndexingList.clear();
        if (pool != null) {
            List<ParsingLinks> parsingLinksList = ParsingLinks.getParsingTasks();
            for (ParsingLinks parsingTask : parsingLinksList) {
                parsingTask.interruptTask();
            }
            pool.shutdownNow();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                pool.shutdownNow();
            }
        }
        List<SiteForIndexing> sitesIndexingNowList = siteRepository.findBySiteStatus(SiteStatus.INDEXING);
        for (SiteForIndexing site : sitesIndexingNowList) {
            ResultForIndexing resultForIndexing = new ResultForIndexing();
            if (!site.getSiteStatus().equals(SiteStatus.INDEXING)) {
                resultForIndexing.setResult(false);
                resultForIndexing.setError("Индексация не запущена");
                resultForIndexingList.add(resultForIndexing);
                continue;
            }
                site.setSiteStatus(SiteStatus.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                resultForIndexing.setResult(true);
                resultForIndexingList.add(resultForIndexing);
                siteRepository.save(site);
        }
        return resultForIndexingList;
    }



    public List<ResultForIndexing> addSite() {
        List<Site> siteList = sites.getSites();
        if (siteList == null) {
            return resultForIndexingList;
        }
        List<SiteForIndexing> siteForIndexingList = siteRepository.findAll();
        for (Site site : siteList) {
            if(checkSiteIndexing(site,siteForIndexingList)){
                continue;
            }
            ResultForIndexing resultForIndexing = new ResultForIndexing();
            addSiteInSiteList(site);
            resultForIndexing.setResult(true);
            resultForIndexingList.add(resultForIndexing);
        }
        siteRepository.saveAll(sitesForStartIndexingList);
        return resultForIndexingList;
    }

    public void deleteSitesAllData() {
        if (siteRepository.count() > 0) {
            siteRepository.deleteAll();
        }
    }

    public void startIndexingAllSites() {
        long start = System.currentTimeMillis();
        List<ParsingLinks> tasks = new ArrayList<>();
        for (SiteForIndexing siteForIndexing : sitesForStartIndexingList) {
            ParsingLinks task = new ParsingLinks(siteForIndexing, siteForIndexing.getUrl(), 0, pageRepository, siteRepository);
            tasks.add(task);
        }
        for (ParsingLinks task : tasks) {
            pool.execute(task);
        }
        pool.shutdown();
        try {
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long finish = System.currentTimeMillis();
        System.out.println("Программа выполнялась: " + (finish - start) / 1000 + " сек.");
    }

    public void addSiteInSiteList(Site site) {
        SiteForIndexing newSiteForIndexing = new SiteForIndexing();
        newSiteForIndexing.setName(site.getName());
        newSiteForIndexing.setUrl(site.getUrl());
        newSiteForIndexing.setSiteStatus(SiteStatus.INDEXING);
        sitesForStartIndexingList.add(newSiteForIndexing);
    }

    public boolean checkSiteIndexing(Site site, List<SiteForIndexing> siteForIndexingList) {
        if(siteForIndexingList== null){
            return false;
        }
        for (SiteForIndexing siteForIndexing : siteForIndexingList) {
            if (!siteForIndexing.getUrl().equals(site.getUrl())) {
                return false;
            }
            if (siteForIndexing.getSiteStatus() == SiteStatus.INDEXING) {
                ResultForIndexing resultForIndexing = new ResultForIndexing();
                resultForIndexing.setResult(false);
                resultForIndexing.setError("Индексация уже запущена");
                resultForIndexingList.add(resultForIndexing);
                return true;
            }
        }
        return false;
    }
}
