package searchengine.searcher;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RequiredArgsConstructor
public class LemmaSearcher {

    LuceneMorphology luceneMorph;
    private static final String[] particles = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    public Map<String, Integer> collectLemmas(String text) {
        try {
            luceneMorph = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        HashMap<String, Integer> lemmas = new HashMap<>();
        String[] words = splitIntoWords(text);

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            List<String> morphInfoWords = luceneMorph.getMorphInfo(word);
            if (hasWordsOfParticle(morphInfoWords)) {
                continue;
            }
            List<String> normalFormsWords = luceneMorph.getNormalForms(word);
            if (normalFormsWords.isEmpty()) {
                continue;
            }
            String normalWord = normalFormsWords.get(0);
            if (lemmas.containsKey(normalWord)) {
                lemmas.put(normalWord, lemmas.get(normalWord) + 1);
            } else {
                lemmas.put(normalWord, 1);
            }
        }
        return lemmas;
    }

    private boolean hasWordsOfParticle(List<String> list) {
        for (String word : list) {
            if (hasWordOfParticle(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasWordOfParticle(String word) {
        for (String particle : particles) {
            if (word.contains(particle)) {
                return true;
            }
        }
        return false;
    }

    private String[] splitIntoWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^а-я\\s])", " ")
                .trim()
                .split("\\s+");
    }
}
