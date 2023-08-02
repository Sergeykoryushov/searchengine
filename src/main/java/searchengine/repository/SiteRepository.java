package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Page;
import searchengine.model.SiteForIndexing;

public interface SiteRepository extends JpaRepository<SiteForIndexing,Integer> {
    SiteForIndexing findByUrl(String url);
}
