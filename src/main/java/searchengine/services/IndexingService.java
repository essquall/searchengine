package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;

import java.io.IOException;

public interface IndexingService {
    IndexingResponse startIndexing();

    IndexingResponse stopIndexing();

    IndexingResponse indexPage(String pagePath);

    void savePage(String child) throws InterruptedException, IOException;

    void handleLastError(String url);

}
