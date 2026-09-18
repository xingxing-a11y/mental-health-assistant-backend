package com.mindharbor.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@TableName("knowledge_documents")
public class KnowledgeDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

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
