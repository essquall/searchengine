package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "index_numbers")
@Getter
@Setter
public class Index {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Page page;

    @ManyToOne(fetch = FetchType.LAZY)
    private Lemma lemma;

    @Column(name = "lemmas_count", nullable = false)
    private float lemmasCount;
}
