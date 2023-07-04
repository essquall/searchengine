package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Long> {

    @Query(value = "SELECT * from lemmas where lemma = :nameOfLemma", nativeQuery = true)
    Lemma findLemmaByName(String nameOfLemma);
}
