package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.*;
import searchengine.model.SiteForIndexing;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
            OneSiteStatisticsInfo siteStatisticsInfo = new OneSiteStatisticsInfo(site, errors, statuses);
           createDetailedStatisticsItem(siteStatisticsInfo, total, detailed);
        }
        return createResponse(total, detailed);
    }


    public void setDataForNonIndexedSites(OneSiteStatisticsInfo info, TotalStatistics total, List<DetailedStatisticsItem> detailed){
        DetailedStatisticsItem item = info.getItem();
        item.setStatus("NO INFORMATION ON INDEXING");
        String lastError = info.getErrors()[2];
        item.setError(lastError);
        item.setStatusTime(LocalDateTime.now());
        total.setPages(total.getPages());
        total.setLemmas(total.getLemmas());
        detailed.add(item);
    }

    public void setDataForIndexedSites(OneSiteStatisticsInfo siteStatisticsInfo, TotalStatistics total, List<DetailedStatisticsItem> detailed) {
        SiteForIndexing siteFromRepository = siteRepository.findByUrl(StartIndexingServiceImpl.addSlashToEnd(siteStatisticsInfo.getSite().getUrl()));
        DetailedStatisticsItem item = siteStatisticsInfo.getItem();
        String status = siteFromRepository.getSiteStatus().toString();
        for (String statusSite : siteStatisticsInfo.getStatuses()) {
            if (statusSite.equals(status)) {
                status = statusSite;
                break;
            }
        }
        item.setStatus(status);
        String lastError = siteFromRepository.getLastError();
        if(lastError == null){
            lastError = siteStatisticsInfo.getErrors()[2];
        }
        item.setError(lastError);
        item.setStatusTime(siteFromRepository.getStatusTime());
        total.setPages(total.getPages() + item.getPages());
        total.setLemmas(total.getLemmas() + item.getLemmas());
        detailed.add(item);
    }


    public StatisticsResponse createResponse(TotalStatistics total, List<DetailedStatisticsItem> detailed){
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    public void createDetailedStatisticsItem(OneSiteStatisticsInfo siteStatisticsInfo, TotalStatistics total, List<DetailedStatisticsItem> detailed) {
        Site site = siteStatisticsInfo.getSite();
        String [] errors = siteStatisticsInfo.getErrors();
        String [] statuses = siteStatisticsInfo.getStatuses();
        SiteForIndexing siteFromRepository = siteRepository.findByUrl(StartIndexingServiceImpl.addSlashToEnd(site.getUrl()));
        DetailedStatisticsItem item = new DetailedStatisticsItem();
        item.setName(site.getName());
        item.setUrl(site.getUrl());
        if (siteFromRepository != null) {
           int pages = pageRepository.findBySiteId(siteFromRepository.getId()).size();
           int lemmas = lemmaRepository.findBySiteId(siteFromRepository.getId()).size();
            item.setPages(pages);
            item.setLemmas(lemmas);
        }
        OneSiteStatisticsInfo siteStatisticsInfoWithDetailedStatisticsItem = new OneSiteStatisticsInfo(site, errors, statuses, item);
        if (siteFromRepository == null) {
            setDataForNonIndexedSites(siteStatisticsInfoWithDetailedStatisticsItem, total, detailed);
            return;
        }
        setDataForIndexedSites(siteStatisticsInfoWithDetailedStatisticsItem, total, detailed);
    }
}
