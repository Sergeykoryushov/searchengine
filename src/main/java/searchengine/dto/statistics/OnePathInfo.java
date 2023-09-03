package searchengine.dto.statistics;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Element;
import searchengine.model.SiteForIndexing;

@Data
public class OnePathInfo {
    private final SiteForIndexing siteForIndexing;
    private final int statusCode;
    private final SearchLemmas searchLemmas;
    private final String html;
}
