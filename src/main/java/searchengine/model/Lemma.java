package searchengine.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(indexes = {@Index(name = "idx_lemma_site_id", columnList = "lemma, site_id")})
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private  int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id",referencedColumnName = "id")
    private SiteForIndexing site;

    @Column(nullable = false)
    private String lemma;

    @Column(nullable = false)
    private int frequency;

    @OneToMany(mappedBy = "lemma", cascade = CascadeType.REMOVE)
    private List<SearchIndex> searchIndexes;
}
