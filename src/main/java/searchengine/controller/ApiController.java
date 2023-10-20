package searchengine.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.response.IndexingResponse;
import searchengine.dto.response.SearchResponse;
import searchengine.dto.response.StatisticsResponse;
import searchengine.exception.IndexingAlreadyRunningException;
import searchengine.exception.IndexingNotRunningException;
import searchengine.exception.InvalidUrlException;
import searchengine.service.indexing.IndexPageService;
import searchengine.service.indexing.StartIndexingService;
import searchengine.service.indexing.StatisticsService;
import searchengine.service.indexing.StopIndexingService;
import searchengine.service.search.SearchService;

import java.io.IOException;
import java.net.MalformedURLException;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final StartIndexingService startIndexingService;
    private final SearchService searchService;
    private final StopIndexingService stopIndexingService;
    private final IndexPageService indexPageService;

    @GetMapping("/statistics")
    public StatisticsResponse statistics() {
        return statisticsService.getStatistics();
    }

    @GetMapping("/startIndexing")
    public IndexingResponse startIndexing() throws IndexingAlreadyRunningException, InterruptedException {
        return startIndexingService.startIndex();
    }

    @PostMapping("/stopIndexing")
    public IndexingResponse stopIndexing() throws InterruptedException, IndexingNotRunningException {
        return stopIndexingService.stopIndex();
    }

    @PostMapping("/indexPage")
    public IndexingResponse indexPage(@RequestParam String url) throws InvalidUrlException, IOException, InterruptedException {
        return indexPageService.indexPageByUrl(url);
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam String query,
                                 @RequestParam(required = false, defaultValue = "0") int offset,
                                 @RequestParam(required = false, defaultValue = "10") int limit,
                                 @RequestParam(required = false) String site) {
        return searchService.search(query, offset, limit, site);
    }
}