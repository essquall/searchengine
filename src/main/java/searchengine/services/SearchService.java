package searchengine.services;

import searchengine.config.Site;
import searchengine.dto.search.SearchResponse;

public interface SearchService {
    SearchResponse search(String query, Site site, int offset, int limit);
}
