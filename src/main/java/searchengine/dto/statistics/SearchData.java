package searchengine.dto.statistics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"error", "site", "siteName", "uri", "title", "snippet", "relevance"})
public class SearchData {
    private boolean result;
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private double relevance;
    private String error;
}
