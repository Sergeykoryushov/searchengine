package searchengine.dto.statistics;

import lombok.Data;
import searchengine.model.SiteForIndexing;
import searchengine.services.SearchLemmasImp;

@Data
public class OnePathInfo {
    private final SiteForIndexing siteForIndexing;
    private final int statusCode;
    private final SearchLemmasImp searchLemmas;
    private final String html;


    public OnePathInfo(SiteForIndexing siteForIndexing, int statusCode, SearchLemmasImp searchLemmas, String html) {
        this.siteForIndexing = siteForIndexing;
        this.statusCode = statusCode;
        this.searchLemmas = searchLemmas;
        this.html = html;
    }

    public OnePathInfo(SiteForIndexing siteForIndexing, int statusCode, String html) {
        this.siteForIndexing = siteForIndexing;
        this.statusCode = statusCode;
        this.searchLemmas = null;
        this.html = html;
    }
}