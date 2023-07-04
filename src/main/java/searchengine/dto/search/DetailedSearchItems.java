package searchengine.dto.search;

import lombok.Data;

@Data
public class DetailedSearchItems {
    private String url;
    private String name;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;
}
