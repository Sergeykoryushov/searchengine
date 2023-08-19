package searchengine.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchIndex {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private  int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id",referencedColumnName = "id")
    private Page page;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "lemma_id", referencedColumnName = "id")
    private Lemma lemma;

    @Column(name = "ranking",nullable = false)
    private float rank;


}
