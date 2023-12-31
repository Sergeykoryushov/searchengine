package searchengine.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import searchengine.dto.SearchData;

import java.util.List;
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"count", "data", "error"})
public class SearchResponse {
    private boolean result;
    private int count;
    private List<SearchData> data;
    private String error;
}
