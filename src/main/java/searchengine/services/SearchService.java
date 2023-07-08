package searchengine.services;

import searchengine.config.Site;
import searchengine.dto.search.SearchRequest;
import searchengine.dto.search.SearchResponse;

public interface SearchService {
        SearchResponse search(String query, Site site, int offset, int limit);
}
