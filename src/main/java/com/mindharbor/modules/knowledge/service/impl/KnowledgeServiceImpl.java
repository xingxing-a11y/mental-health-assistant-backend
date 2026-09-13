package com.mindharbor.modules.knowledge.service.impl;

import com.mindharbor.modules.knowledge.dto.KnowledgeDocumentRequest;
import com.mindharbor.modules.knowledge.entity.KnowledgeDocument;
import com.mindharbor.modules.knowledge.mapper.KnowledgeDocumentMapper;
import com.mindharbor.modules.knowledge.service.KnowledgeService;
import java.util.List;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Value;

/** 知识库业务：文档持久化和向量化状态流转。 */
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeDocumentMapper mapper;
    private final VectorStore vectorStore;
    private final int chunkSize;
    private final int chunkOverlap;

    public KnowledgeServiceImpl(
            KnowledgeDocumentMapper mapper,
            VectorStore vectorStore,
            @Value("${rag.chunk-size:1000}") int chunkSize,
            @Value("${rag.chunk-overlap:100}") int chunkOverlap) {
        this.mapper = mapper;
        this.vectorStore = vectorStore;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    @Override
    public List<KnowledgeDocument> list() {
        return mapper.findAll();
    }

    @Override
    public KnowledgeDocument create(KnowledgeDocumentRequest request) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(request.getTitle());
        document.setContent(request.getContent());
        return mapper.save(document);
    }

    @Override
    public KnowledgeDocument update(Long id, KnowledgeDocumentRequest request) {
        KnowledgeDocument document = mapper.findById(id).orElseThrow();
        deleteVectors(document.getId());
        document.setTitle(request.getTitle());
        document.setContent(request.getContent());
        document.setVectorStatus(KnowledgeDocument.VectorStatus.NOT_VECTORIZED);
        document.touch();
        return mapper.save(document);
    }

    @Override
    public void delete(Long id) {
        mapper.findById(id).ifPresent(document -> deleteVectors(document.getId()));
        mapper.deleteById(id);
    }

    @Override
    public KnowledgeDocument vectorize(Long id, boolean rebuild) {
        KnowledgeDocument document = mapper.findById(id).orElseThrow();
        document.setVectorStatus(KnowledgeDocument.VectorStatus.PROCESSING);
        mapper.save(document);
        try {
            deleteVectors(document.getId());
            vectorStore.add(splitDocument(document));
            document.setVectorStatus(KnowledgeDocument.VectorStatus.VECTORIZED);
        } catch (RuntimeException exception) {
            document.setVectorStatus(KnowledgeDocument.VectorStatus.FAILED);
            mapper.save(document);
            throw new IllegalStateException("知识库向量化失败", exception);
        }
        return mapper.save(document);
    }

    private void deleteVectors(Long documentId) {
        vectorStore.delete(new FilterExpressionBuilder()
                .eq("documentId", documentId)
                .build());
    }

    private List<Document> splitDocument(KnowledgeDocument document) {
        String content = document.getContent();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("知识库正文不能为空");
        }
        List<Document> chunks = new ArrayList<>();
        int overlap = Math.min(Math.max(chunkOverlap, 0), chunkSize - 1);
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            String chunk = content.substring(start, end);
            chunks.add(new Document(
                    chunk,
                    java.util.Map.of(
                            "documentId", document.getId(),
                            "title", document.getTitle(),
                            "chunkStart", start,
                            "chunkEnd", end)));
            if (end == content.length()) {
                break;
            }
            start = end - overlap;
        }
        return chunks;
    }
}
