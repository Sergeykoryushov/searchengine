package searchengine.services.indexingImp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SitesListProperties;
import searchengine.dto.response.IndexingResponse;
import searchengine.model.Page;
import searchengine.model.SiteForIndexing;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SearchIndexRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.IndexPageService;
import searchengine.services.indexing.RecursiveLinkParser;
import searchengine.services.searchImp.LemmaSearcherImp;

import javax.transaction.Transactional;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
@RequiredArgsConstructor
public class IndexPageServiceImpl implements IndexPageService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private final SitesListProperties sites;


    @Override
    public IndexingResponse indexPageByUrl(String url) {
        IndexingResponse indexingResponse = new IndexingResponse();
        String baseUrl = extractBaseUrl(url);
        SiteForIndexing siteForIndexing = siteRepository.findByUrl(baseUrl);
        Set<String> pathSet = new CopyOnWriteArraySet<>();
        RecursiveLinkParser recursiveLinkParser = new RecursiveLinkParser(siteForIndexing, url, 0, pageRepository,
                siteRepository, sites, lemmaRepository, searchIndexRepository, pathSet);
        if (siteForIndexing == null) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            return indexingResponse;
        }
        String path = recursiveLinkParser.urlWithoutMainPath(url);
        Page page = pageRepository.findByPathAndSiteId(path, siteForIndexing.getId());
        if (page != null) {
            pageRepository.delete(page);
        }
        if (recursiveLinkParser.checkLink(url)) {
            LemmaSearcherImp searchLemmas = new LemmaSearcherImp(pageRepository, lemmaRepository, searchIndexRepository);
            try {
                boolean updatePath = true;
                recursiveLinkParser.connectingAndIndexingSite(siteForIndexing,searchLemmas, updatePath);
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
