package searchengine.dto.statistics;

import lombok.Data;
import searchengine.config.Site;
@Data
public class OneSiteStatisticsInfo {
    private final Site site;
    private final String [] errors;
    private final String [] statuses;
    private final DetailedStatisticsItem item;

    public OneSiteStatisticsInfo(Site site, String[] errors, String[] statuses, DetailedStatisticsItem item) {
        this.site = site;
        this.errors = errors;
        this.statuses = statuses;
        this.item = item;
    }

    public OneSiteStatisticsInfo(Site site, String[] errors, String[] statuses) {
        this.site = site;
        this.errors = errors;
        this.statuses = statuses;
        this.item = null;
    }
}
