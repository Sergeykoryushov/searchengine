package searchengine.services;

import searchengine.dto.statistics.IndexingResponse;

import java.util.List;

public interface StartIndexingService {
    List<IndexingResponse> startIndex();
    List<IndexingResponse> stopIndex();
    List<IndexingResponse> indexPageByUrl(String url);
}
