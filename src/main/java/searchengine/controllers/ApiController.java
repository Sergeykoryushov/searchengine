package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.dto.statistics.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.SearchService;
import searchengine.services.StartIndexingService;
import searchengine.services.StatisticsService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final StartIndexingService startIndexingService;
    private final SearchService searchService;

    public ApiController(StatisticsService statisticsService, StartIndexingService startIndexingService, SearchService searchService) {
        this.startIndexingService = startIndexingService;
        this.statisticsService = statisticsService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<List<IndexingResponse>> startIndexing() {
        return ResponseEntity.ok(startIndexingService.startIndex());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<List<IndexingResponse>> stopIndexing() {
        return ResponseEntity.ok(startIndexingService.stopIndex());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<List<IndexingResponse>> indexPage(@RequestParam String url) {
        return ResponseEntity.ok(startIndexingService.indexPageByUrl(url));
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam String query,
                                                 @RequestParam(required = false, defaultValue = "0") int offset,
                                                 @RequestParam(required = false, defaultValue = "10") int limit,
                                                 @RequestParam(required = false) String site) {
        return ResponseEntity.ok(searchService.search(query, offset, limit, site));
    }
}