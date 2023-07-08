package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.config.UserAgent;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.indexing.TreeSite;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.utils.LemmaCollector;
import searchengine.utils.SiteParser;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;
    private final UserAgent agent;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    private IndexingServiceImpl service;
    public static AtomicBoolean isIndexing = new AtomicBoolean(false);

    @Override
    public IndexingResponse startIndexing() {
        clearData();
        isIndexing.set(true);
        sites.getSites().forEach(site -> {
            saveSite(site);
            startParseSite(site);
        });
        IndexingResponse response = new IndexingResponse();
        if (isIndexing.get()) {
            response.setResult(true);
            response.setError("");
        } else {
            response.setResult(false);
            response.setError("Индексация уже запущена");
        }
        return response;
    }

    @Override
    public IndexingResponse stopIndexing() {
        isIndexing.set(false);
        IndexingResponse response = new IndexingResponse();
        if (isIndexing.get()) {
            response.setResult(false);
            response.setError("Индексация не запущена");
        } else {
            response.setResult(true);
            response.setError("");
        }
        return response;
    }

    @Override
    public IndexingResponse indexPage(String pagePath) {
        AtomicBoolean isContainSite = new AtomicBoolean(false);
        isIndexing.set(true);

        String path = decodePath(pagePath);
        String rootUrl = findRootUrl(path);
        Site site = findConfigSite(rootUrl);

        if (site != null) {
            isContainSite.set(true);
            saveSite(site);
            savePage(path);
            changeStatusTypeAfterParse(site);
            isIndexing.set(false);
        }

        IndexingResponse response = new IndexingResponse();
        if (isContainSite.get()) {
            response.setResult(true);
            response.setError("");
        } else {
            response.setResult(false);
            response.setError("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
        }
        return response;
    }

    private void saveSite(Site site) {
        SiteEntity newSite = new SiteEntity();
        newSite.setUrl(site.getUrl());
        newSite.setName(site.getName());
        newSite.setType(StatusType.INDEXING);
        newSite.setStatusTime(LocalDateTime.now());
        siteRepository.save(newSite);
    }

    @Override
    public void savePage(String path) {
        //Thread stop condition
        if (!isIndexing.get()) {
            return;
        }
        String rootUrl = findRootUrl(path);
        String shortcut = cutRootUrl(rootUrl, path);
        try {
            Connection.Response response = Jsoup.connect(path)
                    .userAgent(agent.getUserAgent())
                    .referrer(agent.getReferrer()).execute();
            Document document = response.parse();
            SiteEntity site = siteRepository.findSiteByUrl(rootUrl);

            Page page = new Page();
            page.setPath(shortcut);
            page.setSite(site);
            page.setContent(document.html());
            page.setCode(response.statusCode());
            updateStatusTime(site);
            pageRepository.save(page);

            if (!checkResponseCode(page)) {
                saveLemmaAndIndex(page);
            }
        } catch (IOException e) {
            System.out.println(e + " " + path);
            handleLastError(path);
        }
    }

    private void saveLemmaAndIndex(Page page) {
        LemmaCollector searcher = new LemmaCollector();
        Map<String, Integer> lemmas = searcher.collectLemmas(page.getContent());
        lemmas.forEach((name, count) -> {
            Lemma lemma = lemmaRepository.findLemmaByName(name);
            if (lemma == null) {
                lemma = new Lemma();
                lemma.setLemma(name);
                lemma.setSite(page.getSite());
                lemma.setFrequency(1);
            } else {
                lemma.setFrequency(lemma.getFrequency() + 1);
            }
            lemmaRepository.save(lemma);
            Index index = new Index();
            index.setLemma(lemma);
            index.setLemmasCount(count);
            index.setPage(page);
            indexRepository.save(index);
        });
    }

    private void startParseSite(Site site) {
        service = new IndexingServiceImpl(sites, agent, siteRepository, pageRepository, lemmaRepository, indexRepository);//!
        new Thread(() -> {
            TreeSite treeSite = new TreeSite(site.getUrl());
            SiteParser parse = new SiteParser(treeSite, service);
            new ForkJoinPool().invoke(parse);
            changeStatusTypeAfterParse(site);
            isIndexing.set(false);
        }).start();
    }

    private void changeStatusTypeAfterParse(Site site) {
        if (isIndexing.get()) {
            SiteEntity siteEntity = siteRepository.findSiteByUrl(site.getUrl());
            siteEntity.setType(StatusType.INDEXED);
            siteRepository.save(siteEntity);
        } else {
            List<SiteEntity> notIndexedSites = siteRepository.findNotIndexedSites();
            notIndexedSites.forEach(newSite -> {
                newSite.setType(StatusType.FAILED);
                newSite.setLastError("Индексация остановлена пользователем");
                siteRepository.save(newSite);
            });
        }
    }

    @Override
    public Document getDocument(String url) {
        Document document = null;
        try {
            Thread.sleep(100);
            document = Jsoup.connect(url).userAgent(agent.getUserAgent())
                    .referrer(agent.getReferrer()).get();
        } catch (InterruptedException | SocketTimeoutException | UnknownHostException e) {
            System.out.println(e + " " + url);
        } catch (IOException e) {
            System.out.println(e + " " + url);
            handleLastError(url);
        }
        return document;
    }

    private void handleLastError(String url) {
        String rootUrl = findRootUrl(url);
        SiteEntity newSite = siteRepository.findSiteByUrl(rootUrl);
        newSite.setLastError("Failure of indexing");
        newSite.setType(StatusType.FAILED);
        siteRepository.save(newSite);
    }

    private void clearData() {
        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();
    }

    private Site findConfigSite(String url) {
        for (Site site : sites.getSites()) {
            if (site.getUrl().equals(url)) {
                return site;
            }
        }
        return null;
    }

    private void updateStatusTime(SiteEntity newSite) {
        newSite.setStatusTime(LocalDateTime.now());
        siteRepository.save(newSite);
    }

    private boolean checkResponseCode(Page page) {
        String code = String.valueOf(page.getCode());
        return code.startsWith("4") || code.startsWith("5");
    }

    private String findRootUrl(String url) {
        int index = url.indexOf("/", 8) + 1;
        return url.substring(0, index);
    }

    private String cutRootUrl(String url, String path) {
        return path.substring(url.length() - 1);
    }

    private String decodePath(String request) {
        return URLDecoder.decode(request, StandardCharsets.UTF_8).substring(4);
    }
}