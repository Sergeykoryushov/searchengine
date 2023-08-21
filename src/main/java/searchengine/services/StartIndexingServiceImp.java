package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.ParsingLinks;
import searchengine.dto.statistics.ResultForIndexing;
import searchengine.dto.statistics.SearchForLemmas;
import searchengine.model.*;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SearchIndexRepository;
import searchengine.repository.SiteRepository;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
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
    public List<ResultForIndexing> startIndex() {
        deleteSitesAllData();
        List<ResultForIndexing> resultForIndexingList = addSiteForIndexing();
        startIndexingAllSites();
        return resultForIndexingList;
    }

    @Override
    public List<ResultForIndexing> stopIndex() {
        List<ResultForIndexing> resultForIndexingList = new ArrayList<>();
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
        String baseUrl = extractBaseUrl(url);
        SiteForIndexing siteForIndexing = siteRepository.findByUrl(baseUrl);
        ParsingLinks parsingLinks =  new ParsingLinks(siteForIndexing, url, 0, pageRepository,
                siteRepository,sites,lemmaRepository,searchIndexRepository);
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
                parsingLinks.savePageInRepository(statusCode, url, siteForIndexing);
                saveLemma(path,siteForIndexing);
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
                        if (siteFromRepository.getSiteStatus().equals(SiteStatus.INDEXING)) {
                            siteFromRepository.setSiteStatus(SiteStatus.INDEXED);
                            siteRepository.save(siteFromRepository);
                        }
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
//            List<Page> pageList = pageRepository.findAll();
//            for (Page page: pageList) {
//                indexPageByUrl("https://www.playback.ru" + page.getPath());
//            }
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

    public synchronized void saveLemma(String path, SiteForIndexing siteForIndexing) {
        Page updatePage = pageRepository.findByPath(path);
        String updatePageHtml = updatePage.getContent();
        SearchForLemmas searchForLemmas = new SearchForLemmas();
        HashMap<String, Integer> lemmasCountMap = searchForLemmas.gettingLemmasInText(updatePageHtml);
        Set<String> lemmasSet = lemmasCountMap.keySet();
        for (String lemmaForPage : lemmasSet) {
            Lemma lemma = lemmaRepository.findByLemma(lemmaForPage);
            if (lemma != null) {
                int frequency = lemma.getFrequency();
                lemma.setFrequency(frequency + 1);
                lemmaRepository.saveAndFlush(lemma);
                saveSearchIndexInSearchIndexRepository(lemmasCountMap, lemmaForPage, updatePage);
                continue;
            }
            Lemma newLemma = new Lemma();
            newLemma.setFrequency(1);
            newLemma.setLemma(lemmaForPage);
            newLemma.setSite(siteForIndexing);
            lemmaRepository.saveAndFlush(newLemma);
            saveSearchIndexInSearchIndexRepository(lemmasCountMap,lemmaForPage,updatePage);
        }
    }
    public void saveSearchIndexInSearchIndexRepository
            (HashMap<String, Integer> lemmasCountMap, String lemmaForPage, Page updatePage) {
        SearchIndex searchIndex = new SearchIndex();
        Lemma lemma1 = lemmaRepository.findByLemma(lemmaForPage);
        float rank = lemmasCountMap.get(lemmaForPage);
        searchIndex.setLemma(lemma1);
        searchIndex.setPage(updatePage);
        searchIndex.setRank(rank);
        searchIndexRepository.saveAndFlush(searchIndex);
    }
}
