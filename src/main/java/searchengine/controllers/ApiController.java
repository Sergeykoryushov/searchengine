package searchengine.controllers;

import org.springframework.web.bind.annotation.*;
import searchengine.dto.response.IndexingResponse;
import searchengine.dto.response.SearchResponse;
import searchengine.dto.response.StatisticsResponse;
import searchengine.services.indexing.IndexPageService;
import searchengine.services.indexing.StartIndexingService;
import searchengine.services.indexing.StatisticsService;
import searchengine.services.indexing.StopIndexingService;
import searchengine.services.search.SearchService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final StartIndexingService startIndexingService;
    private final SearchService searchService;
    private final StopIndexingService stopIndexingService;
    private final IndexPageService indexPageService;

    public ApiController(StatisticsService statisticsService, StartIndexingService startIndexingService,
                         SearchService searchService, StopIndexingService stopIndexingService,
                         IndexPageService indexPageService) {
        this.startIndexingService = startIndexingService;
        this.statisticsService = statisticsService;
        this.searchService = searchService;
        this.stopIndexingService = stopIndexingService;
        this.indexPageService = indexPageService;
    }

    @GetMapping("/statistics")
    public StatisticsResponse statistics() {
        return statisticsService.getStatistics();
    }

    @GetMapping("/startIndexing")
    public IndexingResponse startIndexing() {
        return startIndexingService.startIndex();
    }

    @GetMapping("/stopIndexing")
    public IndexingResponse stopIndexing() {
        return stopIndexingService.stopIndex();
    }

    @PostMapping("/indexPage")
    public IndexingResponse indexPage(@RequestParam String url) {
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