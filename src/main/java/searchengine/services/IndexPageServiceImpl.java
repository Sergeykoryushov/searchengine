package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.dto.statistics.ParsingLinks;
import searchengine.dto.statistics.SearchLemmas;
import searchengine.model.Page;
import searchengine.model.SiteForIndexing;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SearchIndexRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@Service
@RequiredArgsConstructor
public class IndexPageServiceImpl implements IndexPageService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private final SitesList sites;


    @Override
    public IndexingResponse indexPageByUrl(String url) {
        IndexingResponse indexingResponse = new IndexingResponse();
        String baseUrl = extractBaseUrl(url);
        SiteForIndexing siteForIndexing = siteRepository.findByUrl(baseUrl);
        ParsingLinks parsingLinks = new ParsingLinks(siteForIndexing, url, 0, pageRepository,
                siteRepository, sites, lemmaRepository, searchIndexRepository);
        if (siteForIndexing == null) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            return indexingResponse;
        }
        String path = parsingLinks.urlWithoutMainPath(url);
        Page page = pageRepository.findByPathAndSiteId(path, siteForIndexing.getId());
        if (page != null) {
            pageRepository.delete(page);
        }
        if (parsingLinks.checkLink(url)) {
            SearchLemmas searchLemmas = new SearchLemmas(
                    pageRepository, siteRepository,
                    lemmaRepository, searchIndexRepository);
            try {
                boolean updateOnePath = true;
                parsingLinks.connectingAndIndexingSite(siteForIndexing,searchLemmas, updateOnePath);
            } catch (IOException | InterruptedException exception) {
                exception.printStackTrace();
            }
            searchLemmas.saveLemma(path, siteForIndexing);
            siteRepository.save(siteForIndexing);
            indexingResponse.setResult(true);
        }
        return indexingResponse;
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
}
