package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.WebSite;

@Repository
public interface SiteRepository extends JpaRepository<WebSite, Long> {

    @Query(value = "SELECT * from sites where url LIKE %:url%", nativeQuery = true)
    WebSite findSiteByUrl(String url);
}
