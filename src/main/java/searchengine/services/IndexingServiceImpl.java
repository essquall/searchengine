package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.TreeSite;
import searchengine.model.Page;
import searchengine.model.StatusType;
import searchengine.model.WebSite;
import searchengine.pass.PassSite;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IndexingServiceImpl implements IndexingService {
    //    Delete path "/" from database
    private SitesList configSites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private static ConcurrentSkipListSet<String> children;

    public IndexingServiceImpl(SitesList configSites, SiteRepository siteRepository, PageRepository pageRepository) {
        this.configSites = configSites;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
    }

    public boolean isIndexing() {
        AtomicBoolean isIndexing = new AtomicBoolean(false);
        indexing();
        isIndexing.set(true);
        return isIndexing.get();
    }

    private void indexing() {
        for (Site configSite : configSites.getSites()) {
//            clearDataSite(configSite.getUrl());
            saveSite(configSite);
            startPassing(configSite);
//            Site site = siteRepository.findSiteByUrl(configSite.getUrl());
//            site.setType(StatusType.INDEXED);
//            siteRepository.save(site);
        }
    }

    private void saveSite(Site configSite) {
        WebSite webSite = new WebSite();
        webSite.setUrl(configSite.getUrl());
        webSite.setStatusTime(LocalDateTime.now());
        webSite.setType(StatusType.INDEXING);
        webSite.setName(configSite.getName());
        siteRepository.save(webSite);
    }

    private void startPassing(Site configSite) {
        IndexingServiceImpl service = new IndexingServiceImpl(configSites, siteRepository, pageRepository);
        new Thread(() -> {
            TreeSite treeSite = new TreeSite(configSite.getUrl());
            PassSite passing = new PassSite(treeSite, service);
            new ForkJoinPool().invoke(passing);
        }).start();
    }

    public ConcurrentSkipListSet<String> getChildren(String url) {
        children = new ConcurrentSkipListSet<>();
        try {
            Thread.sleep(150);
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com")
                    .get();
//            Elements elements = document.select("body").select("a");
            Elements elements = document.select("body").select("a");
            for (Element element : elements) {
                String childPath = element.absUrl("href");
                if (checkFormat(childPath, url)) {
                    children.add(childPath);
                }
            }
        } catch (InterruptedException | SocketTimeoutException | UnknownHostException e) {
            System.out.println(e + " " + url);
        } catch (IOException e) {
            System.out.println(e + " " + url);
            handleLastError(url);
        }
        return children;
    }

    public void savePage(String url, String childPath) throws IOException {
        String rootURL = getRootURL(url);
        String shortcut = cutRootURL(rootURL, childPath);
        WebSite site = siteRepository.findSiteByUrl(rootURL);

        Connection connection = Jsoup.connect(childPath);
        Document document = connection.get();
        Connection.Response response = connection.response();

        Page page = new Page();
        page.setPath(shortcut);
        page.setSite(site);
        page.setContent(document.html());
        page.setCode(response.statusCode());
//        updateStatusTime(site);
        pageRepository.save(page);
    }

    private void handleLastError(String url) {
        String rootUrl = getRootURL(url);
        WebSite site = siteRepository.findSiteByUrl(rootUrl);
        site.setLastError("Failure of indexing");
        site.setType(StatusType.FAILED);
        siteRepository.save(site);
    }

    public String getRootURL(String url) {
        int index = url.indexOf("/", 8) + 1;
        String rootUrl = url.substring(0, index);
        return rootUrl;
    }

    public String cutRootURL(String url, String child) {
        return child.substring(url.length() - 1);
    }

    private boolean checkFormat(String childPath, String url) {
        return childPath.contains(url) && !(childPath.contains("#") || childPath.contains(".jpg") || childPath.contains(".jpeg")
                || childPath.contains(".png") || childPath.contains(".docx") || childPath.contains(".gif")
                || childPath.contains(".webp") || childPath.contains(".pdf") || childPath.contains(".pptx")
                || childPath.contains(".eps") || childPath.contains(".xlsx") || childPath.contains(".doc"));
    }

    private void clearDataSite(String url) {
        long id = siteRepository.findSiteByUrl(url).getId();
        siteRepository.deleteById(id);
    }

    public boolean hasPage(String shortcut) {
        Page page = pageRepository.findPageByPath(shortcut);

//        if ()
        return page == null;
    }

    private void updateStatusTime(WebSite site) {
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }
}