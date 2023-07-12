package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Long> {

    @Query(value = "SELECT SUM(lemmas_count) FROM index_numbers " +
            "JOIN pages ON index_numbers.page_id = pages.id " +
            "WHERE site_id = :siteId", nativeQuery = true)
    int countSiteLemmas(long siteId);

    @Query(value = "SELECT * FROM index_numbers WHERE lemma_id = :lemmaId", nativeQuery = true)
    List<Index> findAllIndexContains (long lemmaId);

    @Query(value = "SELECT SUM(lemmas_count) FROM index_numbers WHERE page_id = :pageId", nativeQuery = true)
    float countPageLemmas (long pageId);

    @Modifying
    @Transactional
    @Query(value = "TRUNCATE TABLE index_numbers", nativeQuery = true)
    void truncateTable();
}
