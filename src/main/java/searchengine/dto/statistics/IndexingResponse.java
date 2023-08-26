package searchengine.dto.statistics;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"error"})
public class IndexingResponse {
    private boolean result;
    private String error;
    public IndexingResponse() {
    }
}
