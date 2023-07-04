package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.dto.search.DetailedSearchItems;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.utils.LemmaCollector;
import searchengine.utils.SnippetBuilder;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final LemmaCollector collector;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final SnippetBuilder snippetBuilder;

    @Override
    public SearchResponse search(String query, Site site, int offset, int limit) {
        Map<String, Integer> requestLemmas = collector.collectLemmas(query);

        List<Lemma> sortedLemmas = sortLemmasByFrequency(requestLemmas);
        List<Long> pagesIdContainsRequestLemmas = findPagesIdContainsRequestLemmas(sortedLemmas);

        Map<Page, Float> absPagesRelevance = calculateAbsPagesRelevance(pagesIdContainsRequestLemmas);
        Map<Page, Float> relPagesRelevance = calculateRelPagesRelevance(absPagesRelevance);
        Map<Float, Page> descSortedRelPagesRelevance = descSortRelPagesRelevance(relPagesRelevance);

        SearchResponse response = convertToResponse(descSortedRelPagesRelevance, query);
        return response;
    }

    private SearchResponse convertToResponse(Map<Float, Page> descSortedRelPagesRelevance, String query) {
        SearchResponse response = new SearchResponse();
        List<SearchData> responseData = new ArrayList<>();

        if (!query.isEmpty()) {
            for (Float relevance : descSortedRelPagesRelevance.keySet()) {
                SearchData data = new SearchData();
                DetailedSearchItems detailed = new DetailedSearchItems();

                Page page = descSortedRelPagesRelevance.get(relevance);
                String content = page.getContent();
                String snippet = snippetBuilder.buildSnippet(query, content);

                detailed.setUrl(page.getSite().getUrl());
                detailed.setName(page.getSite().getName());
                detailed.setUri(page.getPath());
                detailed.setTitle(findTitle(content));
                detailed.setSnippet(snippet);
                detailed.setRelevance(relevance);

                data.setResult(true);
                data.setError("");
                data.setCount(descSortedRelPagesRelevance.size());
                data.setDetailed(detailed);
                responseData.add(data);
            }
            response.setData(responseData);
        } else {
            SearchData data = new SearchData();
            data.setResult(false);
            data.setError("Задан пустой поисковый запро");
            data.setCount(0);
            data.setDetailed(null);
            responseData.add(data);
            response.setData(responseData);
        }
        return response;
    }

    private List<Lemma> sortLemmasByFrequency(Map<String, Integer> requestLemmas) {
        List<Lemma> lemmas = new ArrayList<>();
        requestLemmas.keySet().forEach(nameOfLemma -> {
            Lemma lemma = lemmaRepository.findLemmaByName(nameOfLemma);
            lemmas.add(lemma);
        });
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

    public Map<Float, Page> descSortRelPagesRelevance(Map<Page, Float> relPagesRelevance) {
        Map<Float, Page> descSortedRelPagesRelevance = new TreeMap<>(Collections.reverseOrder());
        for (Page page : relPagesRelevance.keySet()) {
            descSortedRelPagesRelevance.put(relPagesRelevance.get(page), page);
        }
        return descSortedRelPagesRelevance;
    }

    private List<Long> collectPagesIdContainsLemma(List<Lemma> sortedLemmas, int listIndex) {
        List<Index> indexesContainsLemma = indexRepository.findAllIndexContains(sortedLemmas.get(listIndex).getId());
        List<Long> pagesIdContainsRareLemma = new ArrayList<>();
        for (Index index : indexesContainsLemma) {
            pagesIdContainsRareLemma.add(index.getPage().getId());
        }
        return pagesIdContainsRareLemma;
    }

    private String findTitle(String content) {
        String openingTag = "<title>";
        int start = content.indexOf(openingTag) + openingTag.length();
        int end = content.indexOf("</title>");
        return content.substring(start, end);
    }
}
