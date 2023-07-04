package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {

    @Query(value = "SELECT * FROM pages where path = :path", nativeQuery = true)
    Page findPageByPath(String path);//!!!!

    @Query(value = "SELECT * FROM pages where id = :pageId", nativeQuery = true)
    Page findPageById(Long pageId);

    @Query(value = "SELECT COUNT(*) FROM pages where site_id = :siteId", nativeQuery = true)
    int countSitePages(long siteId);

}
