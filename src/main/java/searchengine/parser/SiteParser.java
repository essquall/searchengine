package searchengine.parser;

import searchengine.dto.indexing.TreeSite;
import searchengine.services.IndexingService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.RecursiveAction;

public class SiteParser extends RecursiveAction {

    private TreeSite treeSite;
    private final IndexingService service;
    private static ConcurrentSkipListSet<String> pool = new ConcurrentSkipListSet<>();

    public SiteParser(TreeSite treeSite, IndexingService service) {
        this.treeSite = treeSite;
        this.service = service;
    }

    @Override
    protected void compute() {
        String url = treeSite.getRootURL();
        ConcurrentSkipListSet<String> children = service.collectChildren(url);
        try {
            for (String child : children) {
                String rootURL = service.findRootUrl(child);
                String shortcut = service.cutRootUrl(rootURL, child);
                if (!pool.contains(shortcut)) {
                    pool.add(shortcut);
                    treeSite.addChild(new TreeSite(child));
                    service.savePage(child, rootURL, shortcut);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        List<SiteParser> taskList = new ArrayList<>();
        for (TreeSite child : treeSite.getChildren()) {
            SiteParser task = new SiteParser(child, service);
            task.fork();
            taskList.add(task);
        }
        for (SiteParser task : taskList) {
            task.join();
        }
    }
}