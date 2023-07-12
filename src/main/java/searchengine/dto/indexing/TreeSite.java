package searchengine.dto.indexing;

import lombok.Data;

import java.util.concurrent.CopyOnWriteArrayList;

@Data
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
