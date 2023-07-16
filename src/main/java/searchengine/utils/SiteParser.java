package searchengine.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.UserAgent;
import searchengine.dto.indexing.TreeSite;
import searchengine.services.IndexingService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.RecursiveAction;

public class SiteParser extends RecursiveAction {

    private UserAgent agent;
    private TreeSite treeSite;
    private IndexingService service;

    private static ConcurrentSkipListSet<String> children;
    private static ConcurrentSkipListSet<String> pool = new ConcurrentSkipListSet<>();

    public SiteParser(TreeSite treeSite, IndexingService service, UserAgent agent) {
        this.treeSite = treeSite;
        this.service = service;
        this.agent = agent;
    }

    @Override
    protected void compute() {
        String url = treeSite.getRootUrl();
        try {
            ConcurrentSkipListSet<String> children = collectChildren(url);
            for (String child : children) {
                treeSite.addChild(new TreeSite(child));
            }
            List<SiteParser> taskList = new ArrayList<>();
            for (TreeSite child : treeSite.getChildren()) {
                SiteParser task = new SiteParser(child, service, agent);
                task.fork();
                taskList.add(task);
            }
            for (SiteParser task : taskList) {
                task.join();
            }
        } catch (IOException e) {
            service.handleLastError(url);
        } catch (InterruptedException e) {
            e.getMessage();
        }
    }


    private ConcurrentSkipListSet<String> collectChildren(String url) throws IOException, InterruptedException {
        children = new ConcurrentSkipListSet<>();
        Thread.sleep(100);
        Document document = Jsoup.connect(url).userAgent(agent.getUserAgent())
                .referrer(agent.getReferrer()).get();
        Elements elements = document.select("body").select("a");
        for (Element element : elements) {
            String child = element.absUrl("href");
            if (checkFormat(child, url) && !pool.contains(child)) {
                pool.add(child);
                children.add(child);
                service.savePage(child);
            }
        }
        return children;
    }

    private boolean checkFormat(String child, String url) {
        return child.contains(url) && !(child.contains("#") || child.contains(".jpg") || child.contains(".jpeg")
                || child.contains(".png") || child.contains(".docx") || child.contains(".gif")
                || child.contains(".webp") || child.contains(".pdf") || child.contains(".pptx")
                || child.contains(".eps") || child.contains(".xlsx") || child.contains(".doc"));
    }
}