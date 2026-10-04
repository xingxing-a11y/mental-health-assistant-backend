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

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final VectorStore vectorStore;
    private final SystemSettingService systemSettingService;
    private final AdminOperationLogService adminOperationLogService;

    public KnowledgeServiceImpl(
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            @Lazy VectorStore vectorStore,
            SystemSettingService systemSettingService,
            AdminOperationLogService adminOperationLogService) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.vectorStore = vectorStore;
        this.systemSettingService = systemSettingService;
        this.adminOperationLogService = adminOperationLogService;
    }

    @Override
    public List<KnowledgeDocument> list() {
        return knowledgeDocumentMapper.findAll();
    }

    @Override
    public KnowledgeDocument create(KnowledgeDocumentRequest request) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(request.getTitle());
        document.setContent(request.getContent());
        knowledgeDocumentMapper.insert(document);
        adminOperationLogService.record(
                AdminOperationLog.Type.DOCUMENT,
                "knowledge_document",
                document.getId(),
                "新增知识库文档");
        return document;
    }

    @Override
    public KnowledgeDocument update(Long id, KnowledgeDocumentRequest request) {
        KnowledgeDocument document = knowledgeDocumentMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("知识库文档不存在"));
        deleteVectors(document.getId());
        document.setTitle(request.getTitle());
        document.setContent(request.getContent());
        document.setVectorStatus(KnowledgeDocument.VectorStatus.NOT_VECTORIZED);
        document.touch();
        knowledgeDocumentMapper.updateById(document);
        adminOperationLogService.record(
                AdminOperationLog.Type.DOCUMENT,
                "knowledge_document",
                document.getId(),
                "编辑知识库文档");
        return document;
    }

    @Override
    public void delete(Long id) {
        knowledgeDocumentMapper.findById(id).ifPresent(document -> deleteVectors(document.getId()));
        knowledgeDocumentMapper.deleteById(id);
        adminOperationLogService.record(
                AdminOperationLog.Type.DOCUMENT,
                "knowledge_document",
                id,
                "删除知识库文档");
    }

    @Override
    public KnowledgeDocument vectorize(Long id, boolean rebuild) {
        KnowledgeDocument document = knowledgeDocumentMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("知识库文档不存在"));
        if (!rebuild && document.getVectorStatus() == KnowledgeDocument.VectorStatus.VECTORIZED) {
            return document;
        }
        document.setVectorStatus(KnowledgeDocument.VectorStatus.PROCESSING);
        knowledgeDocumentMapper.updateById(document);
        try {
            deleteVectors(document.getId());
            vectorStore.add(splitDocument(document));
            document.setVectorStatus(KnowledgeDocument.VectorStatus.VECTORIZED);
        } catch (RuntimeException exception) {
            document.setVectorStatus(KnowledgeDocument.VectorStatus.FAILED);
            knowledgeDocumentMapper.updateById(document);
            adminOperationLogService.record(
                    AdminOperationLog.Type.DOCUMENT,
                    "knowledge_document",
                    id,
                    "向量化知识库文档失败");
            throw new IllegalStateException("知识库向量化失败", exception);
        }
        knowledgeDocumentMapper.updateById(document);
        adminOperationLogService.record(
                AdminOperationLog.Type.DOCUMENT,
                "knowledge_document",
                id,
                "向量化知识库文档");
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
