package searchengine.pass;

import org.springframework.stereotype.Component;
import searchengine.dto.statistics.TreeSite;
import searchengine.services.IndexingService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.RecursiveAction;

@Component
public class PassSite extends RecursiveAction {

    private TreeSite treeSite;
    private IndexingService service;

    public PassSite(TreeSite treeSite, IndexingService service) {
        this.treeSite = treeSite;
        this.service = service;
    }

    @Override
    protected void compute() {
        String url = treeSite.getRootUrl();
        ConcurrentSkipListSet<String> children = service.getChildren(url);
        try {
            for (String child : children) {
                String rootURL = service.getRootURL(child);
                String shortcut = service.cutRootURL(url, child);
                if (service.hasPage(shortcut)) {
                    service.savePage(rootURL, child);
                    treeSite.addChildLink(new TreeSite(child));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        List<PassSite> taskList = new ArrayList<>();
        for (TreeSite child : treeSite.getChildrenLinks()) {
            PassSite task = new PassSite(child, service);
            task.fork();
            taskList.add(task);
        }
        for (PassSite task : taskList) {
            task.join();
        }
    }
}