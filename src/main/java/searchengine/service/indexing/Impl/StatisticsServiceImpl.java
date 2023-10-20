package searchengine.service.indexing.Impl;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesConfig;
import searchengine.dto.*;
import searchengine.dto.response.StatisticsResponse;
import searchengine.model.SiteForIndexing;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.service.indexing.StatisticsService;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Getter
@Builder
public class StatisticsServiceImpl implements StatisticsService {
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SiteRepository siteRepository;
    private final SitesConfig sites;

    @Override
    @Transactional
    public StatisticsResponse getStatistics() {
        TotalStatistics total = TotalStatistics.builder()
                .sites(sites.getSites().size())
                .indexing(true)
                .build();
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteConfig> sitesFromConfig = sites.getSites();
        for (SiteConfig site : sitesFromConfig) {
            createDetailedStatisticsItem(site, detailed, total);
        }
        return createResponse(detailed, total);
    }


    public void setDataForNonIndexedSites(TotalStatistics total, List<DetailedStatisticsItem> detailed, DetailedStatisticsItem item){
        item.setStatus("NO INFORMATION ON INDEXING");
        String lastError = "";
        item.setError(lastError);
        item.setStatusTime(LocalDateTime.now());
        total.setPages(total.getPages());
        total.setLemmas(total.getLemmas());
        detailed.add(item);
    }

    public void setDataForIndexedSites(SiteForIndexing siteFromRepository, TotalStatistics total, List<DetailedStatisticsItem> detailed, DetailedStatisticsItem item ) {
        String status = siteFromRepository.getSiteStatus().toString();
        item.setStatus(status);
        String lastError = siteFromRepository.getLastError();
        if(lastError == null){
            lastError = "";
        }
        item.setError(lastError);
        item.setStatusTime(siteFromRepository.getStatusTime());
        total.setPages(total.getPages() + item.getPages());
        total.setLemmas(total.getLemmas() + item.getLemmas());
        detailed.add(item);
    }


    public StatisticsResponse createResponse(List<DetailedStatisticsItem> detailed, TotalStatistics total){
        StatisticsData data = StatisticsData.builder()
                .total(total)
                .detailed(detailed)
                .build();
        return StatisticsResponse.builder()
                .statistics(data)
                .result(true)
                .build();
    }

    public void createDetailedStatisticsItem(SiteConfig site, List<DetailedStatisticsItem> detailed, TotalStatistics total) {
        SiteForIndexing siteFromRepository = siteRepository.findByUrl(StartIndexingServiceImpl.addSlashToEnd(site.getUrl()));
        DetailedStatisticsItem item = DetailedStatisticsItem.builder()
                .name(site.getName())
                .url(site.getUrl())
                .build();

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
