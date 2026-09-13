package com.mindharbor.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    private Long id;

    @Column(nullable = false)

    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "longtext")

    private String content;

    @Enumerated(EnumType.STRING)

    private VectorStatus vectorStatus = VectorStatus.NOT_VECTORIZED;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    public void touch() {
        updatedAt = Instant.now();
    }

    public enum VectorStatus {
        NOT_VECTORIZED,
        PROCESSING,
        VECTORIZED,
        FAILED
    }
}


