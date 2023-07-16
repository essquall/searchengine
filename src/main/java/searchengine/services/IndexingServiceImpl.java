package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
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
import java.net.URLDecoder;
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

    public static AtomicBoolean isIndexing = new AtomicBoolean(false);

    private static final Logger LOGGER = LogManager.getRootLogger();
    private static final Marker ERRORS_MARKER = MarkerManager.getMarker("Errors");
    private static final Marker ACTIONS_MARKER = MarkerManager.getMarker("Actions");

    @Override
    public IndexingResponse startIndexing() {
        clearData();
        LOGGER.info(ACTIONS_MARKER, "Database has been cleared");
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
            response.setError("Indexing already started");
            LOGGER.error(ERRORS_MARKER, response.getError());
        }
        return response;
    }

    @Override
    public IndexingResponse stopIndexing() {
        isIndexing.set(false);
        IndexingResponse response = new IndexingResponse();
        if (isIndexing.get()) {
            response.setResult(false);
            response.setError("Indexing not running");
            LOGGER.error(ERRORS_MARKER, response.getError());
        } else {
            response.setResult(true);
            response.setError("");
        }
        return response;
    }

    @Override
    public IndexingResponse indexPage(String pagePath) {
        clearData();
        LOGGER.info(ACTIONS_MARKER, "Database has been cleared");
        AtomicBoolean isContainSite = new AtomicBoolean(false);
        try {
            isIndexing.set(true);
            String path = decodePath(pagePath);
            String rootUrl = findRootUrl(path);
            Site site = findConfigSite(rootUrl);
            if (site != null) {
                isContainSite.set(true);
                saveSite(site);
                savePage(path);
                changeStatusType(site);
                isIndexing.set(false);
            }
        } catch (InterruptedException e) {
            e.getMessage();
            LOGGER.error(ERRORS_MARKER, e.getMessage());
        } catch (IOException e) {
            handleLastError(pagePath);
        }
        IndexingResponse response = new IndexingResponse();
        if (isContainSite.get()) {
            response.setResult(true);
            response.setError("");
        } else {
            response.setResult(false);
            response.setError("This page is located outside the sites" +
                    " specified in the configuration file");
            LOGGER.error(ERRORS_MARKER, response.getError());
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
    public void savePage(String path) throws InterruptedException, IOException {
        if (!isIndexing.get()) {
            throw new InterruptedException(Thread.currentThread().getName()
                    + " has been interrupted");
        }
        String rootUrl = findRootUrl(path);
        String shortcut = cutRootUrl(rootUrl, path);

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
    }

    private void saveLemmaAndIndex(Page page) {
        LemmaCollector searcher = new LemmaCollector();
        Map<String, Integer> lemmas = searcher.collectLemmas(page.getContent());
        lemmas.forEach((name, count) -> {
            long siteId = page.getSite().getId();
            Lemma lemma = lemmaRepository.findLemmaByNameAndSiteId(name, siteId);

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
        IndexingService service = new IndexingServiceImpl(sites, agent,
                siteRepository, pageRepository, lemmaRepository, indexRepository);
        LOGGER.info(ACTIONS_MARKER, "Site parsing started");
        new Thread(() -> {
            TreeSite treeSite = new TreeSite(site.getUrl());
            SiteParser parse = new SiteParser(treeSite, service, agent);
            new ForkJoinPool().invoke(parse);
            changeStatusType(site);
            isIndexing.set(false);
        }).start();
    }

    private void changeStatusType(Site site) {
        if (isIndexing.get()) {
            SiteEntity siteEntity = siteRepository.findSiteByUrl(site.getUrl());
            siteEntity.setType(StatusType.INDEXED);
            siteRepository.save(siteEntity);
            LOGGER.info(ACTIONS_MARKER, "Site has been indexed");
        } else {
            List<SiteEntity> notIndexedSites = siteRepository.findNotIndexedSites();
            notIndexedSites.forEach(newSite -> {
                newSite.setType(StatusType.FAILED);
                newSite.setLastError("User stopped indexing");
                siteRepository.save(newSite);
                LOGGER.error(ERRORS_MARKER, newSite.getLastError());
            });
        }
    }

    @Override
    public void handleLastError(String url) {
        String rootUrl = findRootUrl(url);
        SiteEntity site = siteRepository.findSiteByUrl(rootUrl);
        site.setLastError("Failure of indexing");
        site.setType(StatusType.FAILED);
        siteRepository.save(site);
        LOGGER.error(ERRORS_MARKER, site.getLastError());
    }

    private void clearData() {
        indexRepository.deleteAllInBatch();
        lemmaRepository.deleteAllInBatch();
        pageRepository.deleteAllInBatch();
        siteRepository.deleteAllInBatch();
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