package searchengine.services.search;

import searchengine.dto.response.SearchResponse;

public interface SearchService {
    SearchResponse search(String query, int offset, int limit, String site);
}
