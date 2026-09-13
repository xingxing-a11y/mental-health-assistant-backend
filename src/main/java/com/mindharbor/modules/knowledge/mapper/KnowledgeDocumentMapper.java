package com.mindharbor.modules.knowledge.mapper;

import com.mindharbor.modules.knowledge.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentMapper extends JpaRepository<KnowledgeDocument, Long> {
}

