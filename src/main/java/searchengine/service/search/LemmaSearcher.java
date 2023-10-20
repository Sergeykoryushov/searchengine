package searchengine.service.search;

import searchengine.model.SiteForIndexing;

import java.io.IOException;


public interface LemmaSearcher {
    void saveLemma(String path, SiteForIndexing siteForIndexing) throws IOException;
}
