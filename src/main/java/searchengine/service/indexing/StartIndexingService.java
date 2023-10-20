package searchengine.service.indexing;

import searchengine.dto.response.IndexingResponse;
import searchengine.exception.IndexingAlreadyRunningException;

public interface StartIndexingService {
    IndexingResponse startIndex() throws InterruptedException, IndexingAlreadyRunningException;
}
