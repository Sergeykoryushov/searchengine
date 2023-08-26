package searchengine.services;

import searchengine.dto.statistics.IndexingResponse;
import searchengine.dto.statistics.SearchResponse;

import java.util.List;

public interface SearchService {
    SearchResponse search(String query);
}
