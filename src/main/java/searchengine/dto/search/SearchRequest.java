package searchengine.dto.search;

import lombok.Data;
import searchengine.config.Site;

@Data
public class SearchRequest {
    private String query;
    private Site site;
    private int offset;
    private int limit;


}
