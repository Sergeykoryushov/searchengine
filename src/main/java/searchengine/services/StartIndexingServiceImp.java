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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class StartIndexingServiceImp implements StartIndexingService{
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SitesList sites;
    private final List<ResultForIndexing> resultForIndexingList = new ArrayList<>();

    @Override
    public List<ResultForIndexing> startIndex() {
        deleteSitesAllData();
        addSite();
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        for (ResultForIndexing resultForIndexing : resultForIndexingList) {
            if (resultForIndexing.getResult()) {
                executor.execute(this::addPage);
            }
        }
        executor.shutdown();
        return resultForIndexingList;
    }


    public List<ResultForIndexing> addSite() {
        List<Site> siteList = sites.getSites();
        if (siteList == null) {
            return resultForIndexingList;
        }
        List<SiteForIndexing> newSiteList = new ArrayList<>();
        for (Site site : siteList) {
            ResultForIndexing resultForIndexing = new ResultForIndexing();
            SiteForIndexing siteForIndexing = siteRepository.findByUrl(site.getUrl());
            if (siteForIndexing != null) {
                if ((siteForIndexing.getSiteStatus() == SiteStatus.INDEXING)) {
                    resultForIndexing.setResult(false);
                    resultForIndexing.setError("Индексация уже запущена");
                    resultForIndexingList.add(resultForIndexing);
                    break;
                }
                addSiteInSiteList(site, newSiteList);
                resultForIndexing.setResult(true);
                resultForIndexingList.add(resultForIndexing);
                break;
            }
            addSiteInSiteList(site, newSiteList);
            resultForIndexing.setResult(true);
            resultForIndexingList.add(resultForIndexing);
        }
        siteRepository.saveAll(newSiteList);
        return resultForIndexingList;
    }

    public void deleteSitesAllData() {
        if (siteRepository.count() > 0) {
            siteRepository.deleteAll();
        }
    }

    public void addPage() {
        long start = System.currentTimeMillis();
        ForkJoinPool pool = new ForkJoinPool();
        List<SiteForIndexing> siteForIndexingList = siteRepository.findAll();
        for (SiteForIndexing siteForIndexing : siteForIndexingList) {
            ParsingLinks task = new ParsingLinks(siteForIndexing, siteForIndexing.getUrl(), 0, pageRepository, siteRepository);
            pool.invoke(task);
            pool.shutdown();
            siteForIndexing.setSiteStatus(SiteStatus.INDEXED);
            siteRepository.save(siteForIndexing);
        }
        long finish = System.currentTimeMillis();
        System.out.println("Программа выполнялась: " + (finish - start) / 1000 + " сек.");
    }

    public void addSiteInSiteList(Site site, List<SiteForIndexing> newSiteList) {
        SiteForIndexing newSiteForIndexing = new SiteForIndexing();
        newSiteForIndexing.setName(site.getName());
        newSiteForIndexing.setUrl(site.getUrl());
        newSiteForIndexing.setSiteStatus(SiteStatus.INDEXING);
        newSiteList.add(newSiteForIndexing);
    }
}
