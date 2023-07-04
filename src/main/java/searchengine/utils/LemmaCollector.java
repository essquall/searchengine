package searchengine.utils;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class LemmaCollector {

    private LuceneMorphology luceneMorph;
    private static final String[] particles = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    public Map<String, Integer> collectLemmas(String text) {
        createRussianMorphology();

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
            List<String> normalFormWords = luceneMorph.getNormalForms(word);
            if (normalFormWords.isEmpty()) {
                continue;
            }
            String normalWord = normalFormWords.get(0);
            if (lemmas.containsKey(normalWord)) {
                lemmas.put(normalWord, lemmas.get(normalWord) + 1);
            } else {
                lemmas.put(normalWord, 1);
            }
        }
        return lemmas;
    }

    public boolean hasWordsOfParticle(List<String> list) {
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

    public LuceneMorphology createRussianMorphology() {
        try {
            luceneMorph = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return luceneMorph;
    }
}