package searchengine.service.indexing.Impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SitesConfig;
import searchengine.dto.response.IndexingResponse;
import searchengine.exception.InvalidUrlException;
import searchengine.model.Page;
import searchengine.model.SiteForIndexing;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SearchIndexRepository;
import searchengine.repository.SiteRepository;
import searchengine.service.indexing.IndexPageService;
import searchengine.service.indexing.RecursiveLinkParser;
import searchengine.service.search.Impl.LemmaSearcherImpl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
@RequiredArgsConstructor
public class IndexPageServiceImpl implements IndexPageService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private final SitesConfig sites;

    @Override
    public IndexingResponse indexPageByUrl(String url) throws InvalidUrlException, IOException, InterruptedException {
        String baseUrl = extractBaseUrl(url);
        SiteForIndexing siteForIndexing = siteRepository.findByUrl(baseUrl);
        RecursiveLinkParser recursiveLinkParser = getRecursiveLinkParser(url, siteForIndexing);
        String path = recursiveLinkParser.urlWithoutMainPath(url);
        Page page = pageRepository.findByPathAndSiteId(path, siteForIndexing.getId());
        if (page != null) {
            pageRepository.delete(page);
        }

        LemmaSearcherImpl searchLemmas = new LemmaSearcherImpl(pageRepository, lemmaRepository, searchIndexRepository);
        boolean updatePath = true;
        recursiveLinkParser.connectingAndIndexingSite(siteForIndexing, searchLemmas, updatePath);
        searchLemmas.saveLemma(path, siteForIndexing);
        siteRepository.save(siteForIndexing);
        IndexingResponse indexingResponse = new IndexingResponse();
        indexingResponse.setResult(true);
        return indexingResponse;
    }

    private RecursiveLinkParser getRecursiveLinkParser(String url, SiteForIndexing siteForIndexing) throws InvalidUrlException {
        if (siteForIndexing == null) {
            throw new InvalidUrlException();
        }
        Set<String> paths = new CopyOnWriteArraySet<>();
        return new RecursiveLinkParser(siteForIndexing, url, 0, pageRepository, siteRepository, sites,
                lemmaRepository, searchIndexRepository, paths);
    }

    public String extractBaseUrl(String fullUrl) throws MalformedURLException {
        URL url = new URL(fullUrl);
        String protocol = url.getProtocol();
        String host = url.getHost();
        return protocol + "://" + host + "/";
    }
}
