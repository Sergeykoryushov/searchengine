package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteForIndexing;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SiteRepository siteRepository;
    private final SitesList sites;

    @Override
    public StatisticsResponse getStatistics() {
        String[] statuses = { "INDEXED", "FAILED", "INDEXING" };
        String[] errors = {
                "Ошибка индексации: главная страница сайта не доступна",
                "Ошибка индексации: сайт не доступен",
                ""
        };

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();
        for(int i = 0; i < sitesList.size(); i++) {
            Site site = sitesList.get(i);
            SiteForIndexing siteFromRepository = siteRepository.findByUrl(StartIndexingServiceImp.addSlashToEnd(site.getUrl()));
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            int pages = 0;
            int lemmas = 0;
            if(siteFromRepository != null){
            pages = pageRepository.findBySiteId(siteFromRepository.getId()).size();
            lemmas = lemmaRepository.findBySiteId(siteFromRepository.getId()).size();
                item.setPages(pages);
                item.setLemmas(lemmas);
            }
            if(siteFromRepository == null){
                setDataForNonIndexedSites(item, errors, total, pages, lemmas, detailed);
                continue;
            }
          setDataForIndexedSites(item, errors, total, pages, lemmas, detailed,siteFromRepository,statuses);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
    public void setDataForNonIndexedSites(DetailedStatisticsItem item, String[] errors,
                  TotalStatistics total, int pages, int lemmas, List<DetailedStatisticsItem> detailed){
        item.setStatus("NO INFORMATION ON INDEXING");
        String lastError = errors[2];
        item.setError(lastError);
        item.setStatusTime(LocalDateTime.now());
        total.setPages(total.getPages() + pages);
        total.setLemmas(total.getLemmas() + lemmas);
        detailed.add(item);
    }

    public void setDataForIndexedSites(DetailedStatisticsItem item, String[] errors,
                TotalStatistics total, int pages, int lemmas, List<DetailedStatisticsItem> detailed,
                SiteForIndexing siteFromRepository,  String[] statuses){
        String status = siteFromRepository.getSiteStatus().toString();
        for (String statusSite: statuses) {
            if(statusSite.equals(status)){
                status = statusSite;
                break;
            }
        }
        item.setStatus(status);
        String lastError = siteFromRepository.getLastError();
        if(lastError == null){
            lastError = errors[2];
        }
        item.setError(lastError);
        item.setStatusTime(siteFromRepository.getStatusTime());
        total.setPages(total.getPages() + pages);
        total.setLemmas(total.getLemmas() + lemmas);
        detailed.add(item);
    }
}
