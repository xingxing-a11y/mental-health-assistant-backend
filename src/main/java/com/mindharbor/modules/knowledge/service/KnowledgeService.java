package com.mindharbor.modules.knowledge.service;

import com.mindharbor.modules.knowledge.dto.KnowledgeDocumentRequest;
import com.mindharbor.modules.knowledge.entity.KnowledgeDocument;
import java.util.List;

public interface KnowledgeService {

    List<KnowledgeDocument> list();

    KnowledgeDocument create(KnowledgeDocumentRequest request);

    KnowledgeDocument update(Long id, KnowledgeDocumentRequest request);

    void delete(Long id);

    /**
     * 向量化知识库文档。{@code rebuild} 为 {@code false} 时幂等：文档已经是
     * {@link KnowledgeDocument.VectorStatus#VECTORIZED} 就原样返回，不重复切片、不重复调用向量模型；
     * 为 {@code true} 时无条件删除旧向量后重新切片与向量化。
     */
    KnowledgeDocument vectorize(Long id, boolean rebuild);

}

