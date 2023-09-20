package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.SiteForIndexing;


public interface SearchLemmas {
    void saveLemma(String path, SiteForIndexing siteForIndexing);
}
