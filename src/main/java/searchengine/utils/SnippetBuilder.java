package searchengine.utils;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
public class SnippetBuilder {

    private final static String openTag = "<b>";
    private final static String closeTag = "</b>";

    private LuceneMorphology luceneMorph;
    private final LemmaCollector lemmaCollector;

    public String buildSnippet(String request, String content) {
        luceneMorph = lemmaCollector.createRussianMorphology();//!

        StringBuilder snippet = new StringBuilder();
        Set<String> requestLemmas = lemmaCollector.collectLemmas(request).keySet();
        String rusContent = content.replaceAll("([^А-я\\s])", " ")
                .replaceAll("\\s+", " ").trim();

        String[] contentWords = rusContent.split("\\s+");
        Map<String, String> contentWordForms = splitContentToForms(contentWords);

        for (String lemma : requestLemmas) {
            String sourceForm = contentWordForms.get(lemma);
            int startIndex = rusContent.indexOf(sourceForm);

            int limitLengthLine = 70;
            String snippetLine = rusContent.substring(startIndex);
            if (snippetLine.length() > limitLengthLine) {
                snippetLine = snippetLine.substring(0, limitLengthLine);
            }
            snippetLine = boldingLemmas(sourceForm, snippetLine);
            snippet.append(snippetLine).append("\n");
        }
        snippet.append("...");
        return snippet.toString();
    }

    private Map<String, String> splitContentToForms(String[] contentWords) {
        Map<String, String> contentWordForms = new HashMap<>();
        for (String sourceForm : contentWords) {
            String lowCaseForm = sourceForm.toLowerCase();
            List<String> morphInfoWords = luceneMorph.getMorphInfo(lowCaseForm);
            if (lemmaCollector.hasWordsOfParticle(morphInfoWords)) {
                continue;
            }
            List<String> normalFormWords = luceneMorph.getNormalForms(lowCaseForm);
            if (normalFormWords.isEmpty()) {
                continue;
            }
            String normalForm = normalFormWords.get(0);
            contentWordForms.put(normalForm, sourceForm);
        }
        return contentWordForms;
    }

    private String boldingLemmas(String sourceForm, String snippetLine) {
        String boldWord = openTag + sourceForm + closeTag;
        snippetLine = snippetLine.replaceAll(sourceForm, boldWord);
        return snippetLine;
    }
}
