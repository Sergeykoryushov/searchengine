package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Lemma;

import java.util.List;

public interface LemmaRepository extends JpaRepository<Lemma,Integer> {
    Lemma findByLemma(String lemma);
    List<Lemma> findBySiteId(int siteId);
}
