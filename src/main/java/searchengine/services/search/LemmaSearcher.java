package searchengine.services.search;

import searchengine.model.SiteForIndexing;


public interface LemmaSearcher {
    void saveLemma(String path, SiteForIndexing siteForIndexing);
}
