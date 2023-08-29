package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.dto.statistics.IndexingResponse;

import java.util.List;

public interface StopIndexingService {
    IndexingResponse stopIndex();
}
