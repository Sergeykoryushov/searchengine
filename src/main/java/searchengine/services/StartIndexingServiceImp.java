package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.ParsingLinks;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.dto.statistics.SearchLemmas;
import searchengine.model.*;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SearchIndexRepository;
import searchengine.repository.SiteRepository;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class StartIndexingServiceImp implements StartIndexingService{
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private  final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private final SitesList sites;
    @Getter
    private static  List<ParsingLinks> tasks;
    private  final List<SiteForIndexing> sitesForStartIndexingList = new ArrayList<>();
    private final List<Thread> threadList = new ArrayList<>();
    private boolean stopAllIndexing = false;


    @Override
    public List<IndexingResponse> startIndex() {
        deleteSitesAllData();
        List<IndexingResponse> indexingResponseList = addSiteForIndexing();
        startIndexingAllSites();
        return indexingResponseList;
    }

    @Override
    public List<IndexingResponse> stopIndex() {
        List<IndexingResponse> indexingResponseList = new ArrayList<>();
        for (Thread thread: threadList){
            if(thread.isAlive()){
                stopAllIndexing = true;
                List<ParsingLinks> parsingLinksList = ParsingLinks.getParsingTasks();
                for (ParsingLinks parsingTask : parsingLinksList) {
                    parsingTask.interruptTask();
                }
            }
        }
        for (Thread thread : threadList) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        List<SiteForIndexing> sitesIndexingNowList = siteRepository.findAll();
        for (SiteForIndexing site : sitesIndexingNowList) {
            IndexingResponse indexingResponse = new IndexingResponse();
            if (!site.getSiteStatus().equals(SiteStatus.INDEXING)) {
                indexingResponse.setResult(false);
                indexingResponse.setError("Индексация не запущена");
                indexingResponseList.add(indexingResponse);
                continue;
            }
                site.setSiteStatus(SiteStatus.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                indexingResponse.setResult(true);
                indexingResponseList.add(indexingResponse);
                siteRepository.save(site);
        }
        return indexingResponseList;
    }

    @Override
    public List<IndexingResponse> indexPageByUrl(String url){
        List<IndexingResponse> indexingResponseList = new ArrayList<>();
        IndexingResponse indexingResponse = new IndexingResponse();
        String baseUrl = extractBaseUrl(url);
        SiteForIndexing siteForIndexing = siteRepository.findByUrl(baseUrl);
        ParsingLinks parsingLinks =  new ParsingLinks(siteForIndexing, url, 0, pageRepository,
                siteRepository,sites,lemmaRepository,searchIndexRepository);
        if(siteForIndexing == null){
            indexingResponse.setResult(false);
            indexingResponse.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            indexingResponseList.add(indexingResponse);
            return indexingResponseList;
        }
        String path = parsingLinks.urlWithoutMainPath(url);
        Page page = pageRepository.findByPathAndSiteId(path, siteForIndexing.getId());
        parsingLinks.setUrl(siteForIndexing.getUrl());
        if(page != null){
            pageRepository.delete(page);
        }
            if(parsingLinks.checkPath(url)){
                int statusCode = HttpStatus.OK.value();
                parsingLinks.savePageInRepository(statusCode, url, siteForIndexing);
                SearchLemmas searchLemmas = new SearchLemmas(
                        pageRepository,siteRepository,
                        lemmaRepository,searchIndexRepository);
                searchLemmas.saveLemma(path,siteForIndexing);
                siteRepository.save(siteForIndexing);
                indexingResponse.setResult(true);
                indexingResponseList.add(indexingResponse);
            }

        return indexingResponseList;
    }

    public List<IndexingResponse> addSiteForIndexing() {
        List<Site> siteList = sites.getSites();
        List<IndexingResponse> indexingResponseList = new ArrayList<>();
        List<SiteForIndexing> siteForIndexingList = siteRepository.findAll();
        for (Site site : siteList) {
            if(checkSiteIndexing(site,siteForIndexingList, indexingResponseList)){
                continue;
            }
            IndexingResponse indexingResponse = new IndexingResponse();
            addSiteInSiteList(site);
            indexingResponse.setResult(true);
            indexingResponseList.add(indexingResponse);
        }
        siteRepository.saveAll(sitesForStartIndexingList);
        return indexingResponseList;
    }

    public void deleteSitesAllData() {
        if (siteRepository.count() > 0) {
            siteRepository.deleteAll();
        }
    }

    public void startIndexingAllSites() {
        long start = System.currentTimeMillis();
        tasks = new ArrayList<>();
        for (SiteForIndexing siteForIndexing : sitesForStartIndexingList) {
            ParsingLinks task = new ParsingLinks(siteForIndexing, siteForIndexing.getUrl(), 0,
                    pageRepository, siteRepository, sites, lemmaRepository, searchIndexRepository);
            tasks.add(task);
        }
        for (ParsingLinks task: tasks) {
            Runnable runnable = ()->{
                    ForkJoinPool pool = new ForkJoinPool();
                    pool.invoke(task);
                    pool.shutdown();
                    if (pool.awaitQuiescence(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
                        if(stopAllIndexing){
                            return;
                        }
                        SiteForIndexing siteFromRepository = siteRepository.findByUrl(task.getSite().getUrl());
                        if (!siteFromRepository.getSiteStatus().equals(SiteStatus.INDEXING)) {
                            return;
                        }
                            siteFromRepository.setSiteStatus(SiteStatus.INDEXED);
                            siteRepository.save(siteFromRepository);

                    }
            };
            Thread thread = new Thread(runnable);
            thread.start();
            threadList.add(thread);
        }
        for (Thread thread : threadList) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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

    public boolean checkSiteIndexing(Site site, List<SiteForIndexing> siteForIndexingList, List<IndexingResponse> indexingResponseList) {
        if(siteForIndexingList== null){
            return false;
        }
        for (SiteForIndexing siteForIndexing : siteForIndexingList) {
            if (!siteForIndexing.getUrl().equals(site.getUrl())) {
                return false;
            }
            if (siteForIndexing.getSiteStatus() == SiteStatus.INDEXING) {
                IndexingResponse indexingResponse = new IndexingResponse();
                indexingResponse.setResult(false);
                indexingResponse.setError("Индексация уже запущена");
                indexingResponseList.add(indexingResponse);
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
    public static String addSlashToEnd(String url) {
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url;
    }
}
