package searchengine.services;

import searchengine.dto.statistics.ResultForIndexing;

import java.util.HashMap;
import java.util.List;

public interface StartIndexingService {
    public List<ResultForIndexing> startIndex();
}
