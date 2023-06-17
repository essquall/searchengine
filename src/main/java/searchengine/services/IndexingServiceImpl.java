package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.indexing.TreeSite;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.searcher.LemmaSearcher;
import searchengine.parser.SiteParser;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    //    Delete path "/" from database
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    private IndexingServiceImpl service;
    private List<Thread> threads = new ArrayList<>();
    private static ConcurrentSkipListSet<String> children;
    private static AtomicBoolean isIndexing = new AtomicBoolean(false);

    @Override
    public IndexingResponse startIndexing() {
        clearData();
        sites.getSites().forEach(site -> {
            saveSite(site);
            startPassSite(site);
        });
        threads.forEach(Thread::start);
        isIndexing.set(true);

        IndexingResponse response = new IndexingResponse();
        if (isIndexing.get()) {
            response.setResult(false);
            response.setError("Индексация уже запущена");
        } else {
            response.setResult(true);
        }
        return response;
    }

    @Override
    public IndexingResponse stopIndexing() {
        threads.forEach(Thread::interrupt);
        isIndexing.set(false);

        siteRepository.findNotIndexedSites().forEach(newSite -> {
            newSite.setType(StatusType.FAILED);
            newSite.setLastError("Индексация остановлена пользователем");
        });
        IndexingResponse response = new IndexingResponse();
        if (isIndexing.get()) {
            response.setResult(true);
        } else {
            response.setResult(false);
            response.setError("Индексация не запущена");
        }
        return response;
    }

    @Override
    public IndexingResponse indexPage(String pagePath) {
        Page page = pageRepository.findPageByPath(pagePath);

        if (!checkResponseCode(page)) {
            LemmaSearcher searcher = new LemmaSearcher();
            Map<String, Integer> lemmas = searcher.collectLemmas(page.getContent());
            saveLemmaAndIndex(lemmas, page);
        }
        IndexingResponse response = new IndexingResponse();
        if (checkPageRelevance(page)) {
            response.setResult(true);
        } else {
            response.setResult(false);
            response.setError("Данная страница находится за пределами сайтов," +
                    "указанных в конфигурационном файле");
        }
        return response;
    }

    public void saveLemmaAndIndex(Map<String, Integer> lemmas, Page page) {
        lemmas.forEach((k, v) -> {
            Lemma lemma = lemmaRepository.findLemma(k);
            if (lemma == null) {
                lemma = new Lemma();
                lemma.setLemma(k);
                lemma.setSite(page.getSite());
                lemma.setFrequency(1);
            } else {
                lemma.setFrequency(lemma.getFrequency() + 1);
            }
            lemmaRepository.save(lemma);

            Index index = new Index();
            index.setLemma(lemma);
            index.setLemmasCount(v);
            index.setPage(page);
            indexRepository.save(index);
        });
    }

    private void saveSite(Site site) {
        SiteEntity newSite = new SiteEntity();
        newSite.setUrl(site.getUrl());
        newSite.setStatusTime(LocalDateTime.now());
        newSite.setType(StatusType.INDEXING);
        newSite.setName(site.getName());
        siteRepository.save(newSite);
    }

    private void startPassSite(Site site) {
        service = new IndexingServiceImpl(sites, siteRepository, pageRepository, lemmaRepository, indexRepository);
        Thread thread = new Thread(() -> {
            TreeSite treeSite = new TreeSite(site.getUrl());
            SiteParser passing = new SiteParser(treeSite, service);
            new ForkJoinPool().invoke(passing);
        });
        threads.add(thread);
    }

    @Override
    public void savePage(String child, String rootUrl, String shortcut) throws IOException {
        SiteEntity newSite = siteRepository.findSiteByUrl(rootUrl);

        Connection connection = Jsoup.connect(child);
        Document document = connection.get();
        Connection.Response response = connection.response();

        Page page = new Page();
        page.setPath(shortcut);
        page.setSite(newSite);
        page.setContent(document.html());
        page.setCode(response.statusCode());
        updateStatusTime(newSite);
        pageRepository.save(page);

        indexPage(shortcut);
    }

    @Override
    public ConcurrentSkipListSet<String> collectChildren(String url) {
        children = new ConcurrentSkipListSet<>();
        try {
            Thread.sleep(150);
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com")
                    .get();
            Elements elements = document.select("body").select("a");
            for (Element element : elements) {
                String child = element.absUrl("href");
                if (checkFormat(child, url)) {
                    children.add(child);
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

    private void handleLastError(String url) {
        String rootUrl = findRootUrl(url);
        SiteEntity newSite = siteRepository.findSiteByUrl(rootUrl);
        newSite.setLastError("Failure of indexing");
        newSite.setType(StatusType.FAILED);
        siteRepository.save(newSite);
    }

    public boolean checkPageRelevance(Page page) {
        SiteEntity site = page.getSite();
        return sites.getSites().contains(site);
    }

    public boolean checkResponseCode(Page page) {
        String code = String.valueOf(page.getCode());
        return code.startsWith("4") || code.startsWith("5");
    }

    @Override
    public String findRootUrl(String url) {
        int index = url.indexOf("/", 8) + 1;
        String rootUrl = url.substring(0, index);
        return rootUrl;
    }

    @Override
    public String cutRootUrl(String url, String child) {
        return child.substring(url.length() - 1);
    }

    private boolean checkFormat(String child, String url) {
        return child.contains(url) && !(child.contains("#") || child.contains(".jpg") || child.contains(".jpeg")
                || child.contains(".png") || child.contains(".docx") || child.contains(".gif")
                || child.contains(".webp") || child.contains(".pdf") || child.contains(".pptx")
                || child.contains(".eps") || child.contains(".xlsx") || child.contains(".doc"));
    }

    private void clearData() {
        pageRepository.deleteAll();
        siteRepository.deleteAll();
        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
    }

    private void updateStatusTime(SiteEntity newSite) {
        newSite.setStatusTime(LocalDateTime.now());
        siteRepository.save(newSite);
    }
}