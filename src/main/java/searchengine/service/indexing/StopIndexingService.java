package searchengine.service.indexing;

import searchengine.dto.response.IndexingResponse;
import searchengine.exception.IndexingNotRunningException;

public interface StopIndexingService {
    IndexingResponse stopIndex() throws InterruptedException, IndexingNotRunningException;
}
