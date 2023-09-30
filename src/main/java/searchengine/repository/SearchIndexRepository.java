package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.SearchIndex;

import java.util.List;

public interface SearchIndexRepository extends JpaRepository<SearchIndex,Integer> {
    @Query("SELECT si.page.id FROM SearchIndex si WHERE si.lemma.id = :lemmaId")
    List<Integer> findPageIdsByLemmaId(int lemmaId);
    List<SearchIndex> findByLemmaIdInAndPageId(List<Integer> lemmaIds, Integer pageId);
    SearchIndex findByLemmaIdAndPageId(int lemmaId, int pageId);
}
