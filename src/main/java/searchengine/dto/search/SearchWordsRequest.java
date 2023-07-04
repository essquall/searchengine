package searchengine.dto.search;

import lombok.Data;
import searchengine.config.Site;

@Data
public class SearchWordsRequest {
    private String query;
    private Site site;
    private Integer offset;
    private Integer limit;

}
