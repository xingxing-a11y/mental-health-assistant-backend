package com.mindharbor.modules.knowledge.service;

import com.mindharbor.modules.knowledge.dto.KnowledgeDocumentRequest;
import com.mindharbor.modules.knowledge.entity.KnowledgeDocument;
import java.util.List;

public interface KnowledgeService {

    List<KnowledgeDocument> list();

    KnowledgeDocument create(KnowledgeDocumentRequest request);

    KnowledgeDocument update(Long id, KnowledgeDocumentRequest request);

    void delete(Long id);

    KnowledgeDocument vectorize(Long id, boolean rebuild);

}

