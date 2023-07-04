package searchengine.dto.indexing;

import lombok.Getter;

import java.util.concurrent.CopyOnWriteArrayList;

@Getter
public class TreeSite {

    private String rootUrl;
    private CopyOnWriteArrayList<TreeSite> children;

    public TreeSite(String rootUrl) {
        this.rootUrl = rootUrl;
        children = new CopyOnWriteArrayList<>();
    }

    public void addChild(TreeSite child) {
        children.add(child);
    }
}
