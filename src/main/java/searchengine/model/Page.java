package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
//@Table(name = "pages", indexes = @Index(name = "pathPage", columnList = "path"))
@Table(name = "pages")
@Getter
@Setter
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private WebSite site;

//    @Column(name = "path", nullable = false, unique = true, columnDefinition = "VARCHAR(255)")
//    @Column(name = "path", nullable = false, columnDefinition = "TEXT, UNIQUE KEY pathPage Index(path(512))")
    @Column(name = "path", nullable = false, columnDefinition = "TEXT")
    private String path;

    private int code;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

}
