package searchengine.services;

import java.io.IOException;
import java.util.concurrent.ConcurrentSkipListSet;

public interface IndexingService {
    boolean isIndexing();
    ConcurrentSkipListSet<String> getChildren(String url);
    String getRootURL(String url);
    String cutRootURL(String url, String child);
    void savePage(String url, String childPath) throws IOException;
    boolean hasPage(String shortcut);

    }
