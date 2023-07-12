package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.search.DetailedSearchItems;
import searchengine.dto.search.SearchResponse;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.LemmaCollector;
import searchengine.utils.SnippetBuilder;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final LemmaCollector collector;
    private final SnippetBuilder snippetBuilder;

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        if (query.isEmpty()) return convertToFailedResponse();
        Map<String, Integer> requestLemmas = collector.collectLemmas(query);

        List<Lemma> sortedLemmas = sortLemmasByFrequency(requestLemmas, site);
        List<Long> pagesIdContainsRequestLemmas = findPagesIdContainsRequestLemmas(sortedLemmas);

        Map<Page, Float> absPagesRelevance = calculateAbsPagesRelevance(pagesIdContainsRequestLemmas);
        Map<Page, Float> relPagesRelevance = calculateRelPagesRelevance(absPagesRelevance);
        Map<Float, Page> descSortedRelPagesRelevance = descSortRelPagesRelevance(relPagesRelevance);
        return convertToResponse(descSortedRelPagesRelevance, query, limit);
    }

    private SearchResponse convertToResponse(Map<Float, Page> descSortedRelPagesRelevance, String query, int limit) {
        SearchResponse response = new SearchResponse();
        List<DetailedSearchItems> detailedList = new ArrayList<>();

        int counter = 0;
        for (Float relevance : descSortedRelPagesRelevance.keySet()) {
            if (counter == limit) break;
            Page page = descSortedRelPagesRelevance.get(relevance);
            SiteEntity site = page.getSite();
            String content = page.getContent();
            String snippet = snippetBuilder.buildSnippet(query, content);

            DetailedSearchItems detailed = new DetailedSearchItems();
            detailed.setSite(site.getName());
            detailed.setSiteName(site.getName());
            detailed.setUri(page.getPath());
            detailed.setTitle(findTitle(content));
            detailed.setSnippet(snippet);
            detailed.setRelevance(relevance);
            detailedList.add(detailed);
            counter++;
        }
        response.setResult(true);
        response.setError("");
        response.setCount(descSortedRelPagesRelevance.size());
        response.setData(detailedList);
        return response;
    }

    private SearchResponse convertToFailedResponse() {
        SearchResponse response = new SearchResponse();
        response.setResult(false);
        response.setError("Задан пустой поисковый запрос");
        response.setCount(0);
        response.setData(new ArrayList<>());
        return response;
    }

    private List<Lemma> sortLemmasByFrequency(Map<String, Integer> requestLemmas, String site) {
        List<Lemma> lemmas = new ArrayList<>();
        for (String lemmaName : requestLemmas.keySet()) {
            Lemma lemma;
            if (site != null) {
                long siteId = siteRepository.findSiteByUrl(site).getId();
                lemma = lemmaRepository.findLemmaByNameAndSiteId(lemmaName, siteId);
            } else {
                lemma = lemmaRepository.findRareLemmaByName(lemmaName);
            }
            lemmas.add(lemma);
        }
        List<Lemma> sortedLemmas = lemmas.stream().sorted((lemma1, lemma2) ->
                lemma1.getFrequency().compareTo(lemma2.getFrequency())).collect(Collectors.toList());
        return sortedLemmas;
    }

    private List<Long> findPagesIdContainsRequestLemmas(List<Lemma> sortedLemmas) {
        List<Long> pagesIdContainsAllLemmas = collectPagesIdContainsLemma(sortedLemmas, 0);
        for (int i = 1; i < sortedLemmas.size(); i++) {
            List<Long> pagesIdContainsLemma = collectPagesIdContainsLemma(sortedLemmas, i);
            pagesIdContainsAllLemmas.retainAll(pagesIdContainsLemma);
        }
        return pagesIdContainsAllLemmas.isEmpty() ? new ArrayList<>() : pagesIdContainsAllLemmas;
    }

    private List<Long> collectPagesIdContainsLemma(List<Lemma> sortedLemmas, int listIndex) {
        List<Index> indexesContainsLemma = indexRepository.findAllIndexContains(sortedLemmas.get(listIndex).getId());
        List<Long> pagesIdContainsRareLemma = new ArrayList<>();
        for (Index index : indexesContainsLemma) {
            pagesIdContainsRareLemma.add(index.getPage().getId());
        }
        return pagesIdContainsRareLemma;
    }

    private Map<Page, Float> calculateAbsPagesRelevance(List<Long> pagesIdContainsAllLemmas) {
        Map<Page, Float> absPagesRelevance = new HashMap<>();
        for (Long pageId : pagesIdContainsAllLemmas) {
            Page page = pageRepository.findPageById(pageId);
            float lemmasCount = indexRepository.countPageLemmas(pageId);
            absPagesRelevance.put(page, lemmasCount);
        }
        return absPagesRelevance;
    }

    private Map<Page, Float> calculateRelPagesRelevance(Map<Page, Float> absPagesRelevance) {
        float totalAbcRelevance = 0;
        for (Page page : absPagesRelevance.keySet()) {
            totalAbcRelevance += absPagesRelevance.get(page);
        }
        Map<Page, Float> relPagesRelevance = new HashMap<>();
        for (Page page : absPagesRelevance.keySet()) {
            relPagesRelevance.put(page, absPagesRelevance.get(page) / totalAbcRelevance);
        }
        return relPagesRelevance;
    }

    private Map<Float, Page> descSortRelPagesRelevance(Map<Page, Float> relPagesRelevance) {
        Map<Float, Page> descSortedRelPagesRelevance = new TreeMap<>(Collections.reverseOrder());
        for (Page page : relPagesRelevance.keySet()) {
            descSortedRelPagesRelevance.put(relPagesRelevance.get(page), page);
        }
        return descSortedRelPagesRelevance;
    }

    private String findTitle(String content) {
        String openingTag = "<title>";
        int start = content.indexOf(openingTag) + openingTag.length();
        int end = content.indexOf("</title>");
        return content.substring(start, end);
    }
}
