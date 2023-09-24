package searchengine.services.indexingImp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SiteProperty;
import searchengine.config.SitesListProperties;
import searchengine.dto.*;
import searchengine.dto.response.StatisticsResponse;
import searchengine.model.SiteForIndexing;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.StatisticsService;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SiteRepository siteRepository;
    private final SitesListProperties sites;
    private static final String[] statuses = { "INDEXED", "FAILED", "INDEXING" };
    private static final String[] errors = {
            "Ошибка индексации: главная страница сайта не доступна",
            "Ошибка индексации: сайт не доступен",
            ""
    };

    @Override
    @Transactional
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteProperty> sitesList = sites.getSites();
        for(int i = 0; i < sitesList.size(); i++) {
            SiteProperty site = sitesList.get(i);
           createDetailedStatisticsItem(site, detailed, total);
        }
        return createResponse(detailed, total);
    }


    public void setDataForNonIndexedSites(TotalStatistics total, List<DetailedStatisticsItem> detailed, DetailedStatisticsItem item){
        item.setStatus("NO INFORMATION ON INDEXING");
        String lastError = errors[2];
        item.setError(lastError);
        item.setStatusTime(LocalDateTime.now());
        total.setPages(total.getPages());
        total.setLemmas(total.getLemmas());
        detailed.add(item);
    }

    public void setDataForIndexedSites(SiteForIndexing siteFromRepository, TotalStatistics total, List<DetailedStatisticsItem> detailed, DetailedStatisticsItem item ) {
        String status = siteFromRepository.getSiteStatus().toString();
        for (String statusSite : statuses) {
            if (statusSite.equals(status)) {
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
        total.setPages(total.getPages() + item.getPages());
        total.setLemmas(total.getLemmas() + item.getLemmas());
        detailed.add(item);
    }


    public StatisticsResponse createResponse(List<DetailedStatisticsItem> detailed, TotalStatistics total){
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    public void createDetailedStatisticsItem(SiteProperty site, List<DetailedStatisticsItem> detailed, TotalStatistics total) {
        SiteForIndexing siteFromRepository = siteRepository.findByUrl(StartIndexingServiceImpl.addSlashToEnd(site.getUrl()));
        DetailedStatisticsItem item = new DetailedStatisticsItem();
        item.setName(site.getName());
        item.setUrl(site.getUrl());
        if (siteFromRepository != null) {
           int pages =  pageRepository.countPageBySiteId(siteFromRepository.getId());
           int lemmas = lemmaRepository.countLemmaBySiteId(siteFromRepository.getId());
            item.setPages(pages);
            item.setLemmas(lemmas);
        }
        if (siteFromRepository == null) {
            setDataForNonIndexedSites(total, detailed, item);
            return;
        }
        setDataForIndexedSites(siteFromRepository, total, detailed, item);
    }
}
