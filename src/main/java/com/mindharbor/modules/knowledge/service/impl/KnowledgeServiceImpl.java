package com.mindharbor.modules.knowledge.service.impl;

import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.modules.admin.entity.AdminOperationLog;
import com.mindharbor.modules.admin.service.AdminOperationLogService;
import com.mindharbor.modules.knowledge.dto.KnowledgeDocumentRequest;
import com.mindharbor.modules.knowledge.entity.KnowledgeDocument;
import com.mindharbor.modules.knowledge.mapper.KnowledgeDocumentMapper;
import com.mindharbor.modules.knowledge.service.KnowledgeService;
import com.mindharbor.modules.system.service.SystemSettingService;
import com.mindharbor.modules.system.vo.KnowledgeSettingsVO;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import java.util.ArrayList;

/** 知识库业务：文档持久化和向量化状态流转。 */
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeDocumentMapper mapper;
    private final VectorStore vectorStore;
    private final SystemSettingService systemSettingService;
    private final AdminOperationLogService operationLogService;

    public KnowledgeServiceImpl(
            KnowledgeDocumentMapper mapper,
            @Lazy VectorStore vectorStore,
            SystemSettingService systemSettingService,
            AdminOperationLogService operationLogService) {
        this.mapper = mapper;
        this.vectorStore = vectorStore;
        this.systemSettingService = systemSettingService;
        this.operationLogService = operationLogService;
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
        mapper.insert(document);
        operationLogService.record(
                AdminOperationLog.Type.DOCUMENT,
                "knowledge_document",
                document.getId(),
                "新增知识库文档");
        return document;
    }

    @Override
    public KnowledgeDocument update(Long id, KnowledgeDocumentRequest request) {
        KnowledgeDocument document = mapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("知识库文档不存在"));
        deleteVectors(document.getId());
        document.setTitle(request.getTitle());
        document.setContent(request.getContent());
        document.setVectorStatus(KnowledgeDocument.VectorStatus.NOT_VECTORIZED);
        document.touch();
        mapper.updateById(document);
        operationLogService.record(
                AdminOperationLog.Type.DOCUMENT,
                "knowledge_document",
                document.getId(),
                "编辑知识库文档");
        return document;
    }

    @Override
    public void delete(Long id) {
        mapper.findById(id).ifPresent(document -> deleteVectors(document.getId()));
        mapper.deleteById(id);
        operationLogService.record(
                AdminOperationLog.Type.DOCUMENT,
                "knowledge_document",
                id,
                "删除知识库文档");
    }

    @Override
    public KnowledgeDocument vectorize(Long id, boolean rebuild) {
        KnowledgeDocument document = mapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("知识库文档不存在"));
        document.setVectorStatus(KnowledgeDocument.VectorStatus.PROCESSING);
        mapper.updateById(document);
        try {
            deleteVectors(document.getId());
            vectorStore.add(splitDocument(document));
            document.setVectorStatus(KnowledgeDocument.VectorStatus.VECTORIZED);
        } catch (RuntimeException exception) {
            document.setVectorStatus(KnowledgeDocument.VectorStatus.FAILED);
            mapper.updateById(document);
            throw new IllegalStateException("知识库向量化失败", exception);
        }
        mapper.updateById(document);
        return document;
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
        // 向量化时读取全局配置，覆盖 application.yml 中的默认分片参数
        KnowledgeSettingsVO settings = systemSettingService.knowledgeSettings();
        int chunkSize = Math.max(settings.getChunkSize(), 100);
        int chunkOverlap = Math.min(Math.max(settings.getChunkOverlap(), 0), chunkSize - 1);
        List<Document> chunks = new ArrayList<>();
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
            start = end - chunkOverlap;
        }
        return chunks;
    }
}
