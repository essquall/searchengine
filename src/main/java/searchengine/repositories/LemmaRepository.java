package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Long> {

    @Query(value = "SELECT * from lemmas WHERE lemma = :lemmaName ORDER BY frequency LIMIT 1", nativeQuery = true)
    Lemma findRareLemmaByName(String lemmaName);

    @Query(value = "SELECT * from lemmas WHERE lemma = :lemmaName AND site_id = :siteId", nativeQuery = true)
    Lemma findLemmaByNameAndSiteId(String lemmaName, long siteId);

}
