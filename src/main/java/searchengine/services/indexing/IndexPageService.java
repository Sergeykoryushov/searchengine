package searchengine.services.indexing;

import searchengine.dto.response.IndexingResponse;

public interface IndexPageService {
    IndexingResponse indexPageByUrl(String url);
}
