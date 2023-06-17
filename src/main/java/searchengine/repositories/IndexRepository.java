package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.Index;

public interface IndexRepository extends JpaRepository<Index, Long> {

    @Query(value = "SELECT SUM(lemmas_count) FROM index_numbers " +
            "JOIN pages ON index_numbers.page_id = pages.id " +
            "WHERE site_id = :siteId", nativeQuery = true)
    int countSiteLemmas(long siteId);
}
