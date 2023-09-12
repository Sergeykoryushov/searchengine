package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Lemma;

public interface LemmaRepository extends JpaRepository<Lemma,Integer> {
    Lemma findByLemmaAndSiteId(String lemma, int siteId);
    @Query("SELECT COUNT(l) FROM Lemma l where l.site.id = :siteId")
    Integer countLemmaBySiteId(@Param("siteId") int siteId);
}
