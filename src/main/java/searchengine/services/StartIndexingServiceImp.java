package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.ParsingLinks;
import searchengine.dto.statistics.ResultForIndexing;
import searchengine.model.Page;
import searchengine.model.SiteForIndexing;
import searchengine.model.SiteStatus;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
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
    private static ForkJoinPool pool = new ForkJoinPool();

    @Override
    public List<ResultForIndexing> startIndex() {
        deleteSitesAllData();
        List<ResultForIndexing> resultForIndexingList = addSiteForIndexing();
        startIndexingAllSites();
        return resultForIndexingList;
    }

    @Override
    public List<ResultForIndexing> stopIndex() {
        List<ResultForIndexing> resultForIndexingList = new ArrayList<>();
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

    @Override
    public List<ResultForIndexing> indexPageByUrl(String url){
        List<ResultForIndexing> resultForIndexingList = new ArrayList<>();
        ResultForIndexing resultForIndexing = new ResultForIndexing();
        ParsingLinks parsingLinks = new ParsingLinks();
        String baseUrl = extractBaseUrl(url);
        SiteForIndexing siteForIndexing = siteRepository.findByUrl(baseUrl);
        if(siteForIndexing == null){
            resultForIndexing.setResult(false);
            resultForIndexing.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            resultForIndexingList.add(resultForIndexing);
            return resultForIndexingList;
        }
        String path = parsingLinks.urlWithoutRelativePath(url);
        Page page = pageRepository.findByPath(path);
        parsingLinks.setUrl(siteForIndexing.getUrl());
        if(page != null){
            pageRepository.delete(page);
        }
            if(parsingLinks.checkLink(url)){
                int statusCode = HttpStatus.OK.value();
                parsingLinks.savePageInRepository(statusCode, path, siteForIndexing);
                siteForIndexing.setStatusTime(LocalDateTime.now());
                siteRepository.save(siteForIndexing);
                resultForIndexing.setResult(true);
                resultForIndexingList.add(resultForIndexing);
            }

        return resultForIndexingList;
    }

    public List<ResultForIndexing> addSiteForIndexing() {
        List<Site> siteList = sites.getSites();
        List<ResultForIndexing> resultForIndexingList = new ArrayList<>();
        List<SiteForIndexing> siteForIndexingList = siteRepository.findAll();
        for (Site site : siteList) {
            if(checkSiteIndexing(site,siteForIndexingList,resultForIndexingList)){
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
        newSiteForIndexing.setUrl(addSlashToEnd(site.getUrl()));
        newSiteForIndexing.setSiteStatus(SiteStatus.INDEXING);
        sitesForStartIndexingList.add(newSiteForIndexing);
    }

    public boolean checkSiteIndexing(Site site, List<SiteForIndexing> siteForIndexingList, List<ResultForIndexing> resultForIndexingList) {
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
    public String extractBaseUrl(String fullUrl) {
        try {
            URI uri = new URI(fullUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return scheme + "://" + host + "/";
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return fullUrl;
        }
    }
    public String addSlashToEnd(String url) {
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url;
    }
}
