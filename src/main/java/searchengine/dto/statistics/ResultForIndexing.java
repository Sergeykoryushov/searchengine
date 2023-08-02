package searchengine.dto.statistics;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.sun.istack.NotNull;
import lombok.Data;
import lombok.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"error"})
public class ResultForIndexing {
    private  Boolean result;
    private  String error;
    public ResultForIndexing() {
    }
}
