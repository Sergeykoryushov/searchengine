package searchengine.dto.response;

import lombok.Builder;
import lombok.Data;
import searchengine.dto.StatisticsData;

@Data
@Builder
public class StatisticsResponse {
    private boolean result;
    private StatisticsData statistics;
}
