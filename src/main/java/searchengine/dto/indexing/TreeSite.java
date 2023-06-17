package searchengine.dto.indexing;

import lombok.Getter;

import java.util.concurrent.CopyOnWriteArrayList;

@Getter
public class TreeSite {

    private String rootURL;
    private CopyOnWriteArrayList<TreeSite> children;

    public TreeSite(String rootURL) {
        this.rootURL = rootURL;
        children = new CopyOnWriteArrayList<>();
    }

    public void addChild(TreeSite child) {
        children.add(child);
    }
}
