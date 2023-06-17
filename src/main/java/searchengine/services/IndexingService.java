package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;

import java.io.IOException;
import java.util.concurrent.ConcurrentSkipListSet;

public interface IndexingService {
    IndexingResponse startIndexing();
    IndexingResponse stopIndexing();
    IndexingResponse indexPage(String pagePath);
    ConcurrentSkipListSet<String> collectChildren(String url);
    String findRootUrl(String url);
    String cutRootUrl(String url, String child);
    void savePage(String child, String url, String shortcut) throws IOException;

}
