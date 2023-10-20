package searchengine.service.search;

import searchengine.dto.response.SearchResponse;

import java.io.IOException;

public interface SearchService {
    SearchResponse search(String query, int offset, int limit, String site) throws IOException;
}
