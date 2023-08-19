package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.ResultForIndexing;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.StartIndexingService;
import searchengine.services.StartIndexingServiceImp;
import searchengine.services.StatisticsService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final StartIndexingService startIndexingService;

    public ApiController(StatisticsService statisticsService,StartIndexingService startIndexingService) {
        this.startIndexingService = startIndexingService;
        this.statisticsService = statisticsService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<List<ResultForIndexing>> startIndexing() {
        return ResponseEntity.ok(startIndexingService.startIndex());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<List<ResultForIndexing>> stopIndexing() {
        return ResponseEntity.ok(startIndexingService.stopIndex());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<List<ResultForIndexing>> indexPage(@RequestParam String url) {
        return ResponseEntity.ok(startIndexingService.indexPageByUrl(url));
    }
}
