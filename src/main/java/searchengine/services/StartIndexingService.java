package searchengine.services;

import searchengine.dto.statistics.IndexingResponse;

import java.util.List;

public interface StartIndexingService {
    IndexingResponse startIndex();
    List<IndexingResponse> indexPageByUrl(String url);
}
