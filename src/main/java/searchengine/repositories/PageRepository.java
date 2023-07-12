package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {

    @Query(value = "SELECT * FROM pages WHERE id = :pageId", nativeQuery = true)
    Page findPageById(Long pageId);

    @Query(value = "SELECT COUNT(*) FROM pages WHERE site_id = :siteId", nativeQuery = true)
    int countSitePages(long siteId);

    @Modifying
    @Transactional
    @Query(value = "TRUNCATE TABLE pages", nativeQuery = true)
    void truncateTable();
}
