package searchengine.services;

import searchengine.dto.statistics.ResultForIndexing;

import java.util.HashMap;
import java.util.List;

public interface StartIndexingService {
    List<ResultForIndexing> startIndex();
    List<ResultForIndexing> stopIndex();
    List<ResultForIndexing> indexPageByUrl(String url);
}
