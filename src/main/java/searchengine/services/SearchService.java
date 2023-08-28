package searchengine.services;

import searchengine.dto.statistics.IndexingResponse;
import searchengine.dto.statistics.SearchResponse;

import java.util.List;

public interface SearchService {
    public SearchResponse search(String query, int offset, int limit, String site);
}
