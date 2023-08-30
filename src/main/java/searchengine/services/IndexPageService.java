package searchengine.services;

import searchengine.dto.statistics.IndexingResponse;

import java.util.List;

public interface IndexPageService {
    IndexingResponse indexPageByUrl(String url);
}
