package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Long> {

    @Query(value = "SELECT * from lemmas WHERE lemma = :lemmaName ORDER BY frequency LIMIT 1", nativeQuery = true)
    Lemma findRareLemmaByName(String lemmaName);

    @Query(value = "SELECT * from lemmas WHERE lemma = :lemmaName AND site_id = :siteId", nativeQuery = true)
    Lemma findLemmaByNameAndSiteId(String lemmaName, long siteId);

    @Modifying
    @Transactional
    @Query(value = "TRUNCATE TABLE lemmas", nativeQuery = true)
    void truncateTable();
}
