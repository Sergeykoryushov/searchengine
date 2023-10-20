package searchengine.service.indexing;

import searchengine.dto.response.IndexingResponse;
import searchengine.exception.InvalidUrlException;

import java.io.IOException;

public interface IndexPageService {
    IndexingResponse indexPageByUrl(String url) throws InvalidUrlException, IOException, InterruptedException;
}
