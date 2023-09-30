package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Page;

import java.util.List;

public interface PageRepository extends JpaRepository<Page, Integer> {
    Page findByPathAndSiteId(String path, int siteId);
    List<Page> findByIdIn(List<Integer> pageIds);
    @Query("SELECT COUNT(p) FROM Page p WHERE p.site.id = :siteId")
    Integer countPageBySiteId(@Param("siteId") int siteId);

}