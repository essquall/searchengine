package searchengine.dto.statistics;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.concurrent.CopyOnWriteArrayList;

//@Component
@Getter
public class TreeSite {

    private String rootUrl;
    private CopyOnWriteArrayList<TreeSite> childrenLinks;

    public TreeSite(String rootUrl) {
        this.rootUrl = rootUrl;
        childrenLinks = new CopyOnWriteArrayList<>();
    }

    public void addChildLink(TreeSite child) {
        childrenLinks.add(child);
    }
}
