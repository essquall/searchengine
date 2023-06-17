package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.Lemma;

public interface LemmaRepository extends JpaRepository<Lemma, Long> {

    @Query(value = "SELECT * from lemmas where lemma = :lemma", nativeQuery = true)
    Lemma findLemma(String lemma);
}
