package searchengine.services;

import org.jsoup.nodes.Document;
import searchengine.dto.indexing.IndexingResponse;

public interface IndexingService {
    IndexingResponse startIndexing();

    IndexingResponse stopIndexing();

    IndexingResponse indexPage(String pagePath);

    void savePage(String child);

    Document getDocument(String url);
}
