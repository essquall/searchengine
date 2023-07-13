package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Long> {

    @Query(value = "SELECT * FROM sites WHERE url = :url", nativeQuery = true)
    SiteEntity findSiteByUrl(String url);

    @Query(value = "SELECT * FROM sites WHERE type != 'INDEXED'", nativeQuery = true)
    List<SiteEntity> findNotIndexedSites();

}
