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
@Table(name = "page", indexes = @javax.persistence.Index(name = "path_siteId_index", columnList = "path, site_id", unique = true))
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id",referencedColumnName = "id")
    private SiteForIndexing site;

    @Column(nullable = false, length = 512)
    String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci",nullable = false)
    private String content;

    @OneToMany(mappedBy = "page",cascade = CascadeType.REMOVE)
    private List<SearchIndex> searchIndexes;

}
