package com.mindharbor.modules.knowledge.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.knowledge.dto.KnowledgeDocumentRequest;
import com.mindharbor.modules.knowledge.entity.KnowledgeDocument;
import com.mindharbor.modules.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 知识库管理接口，向量化状态流转由 service 层负责。 */
@RestController
@RequestMapping("/admin/knowledge-documents")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping
    public ApiResponse<List<KnowledgeDocument>> list() {
        log.info("List knowledge documents");
        return ApiResponse.ok(knowledgeService.list());
    }

    @PostMapping
    public ApiResponse<KnowledgeDocument> create(
            @Valid @RequestBody KnowledgeDocumentRequest request) {
        log.info("Create knowledge document");
        return ApiResponse.ok(knowledgeService.create(request));
    }

    @PutMapping("/{documentId}")
    public ApiResponse<KnowledgeDocument> update(
            @PathVariable Long documentId,
            @Valid @RequestBody KnowledgeDocumentRequest request) {
        log.info("Update knowledge document, documentId={}", documentId);
        return ApiResponse.ok(knowledgeService.update(documentId, request));
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> delete(@PathVariable Long documentId) {
        log.info("Delete knowledge document, documentId={}", documentId);
        knowledgeService.delete(documentId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{documentId}/vectorize")
    public ApiResponse<KnowledgeDocument> vectorize(@PathVariable Long documentId) {
        log.info("Vectorize knowledge document, documentId={}", documentId);
        return ApiResponse.ok(knowledgeService.vectorize(documentId, false));
    }

    @PostMapping("/{documentId}/re-vectorize")
    public ApiResponse<KnowledgeDocument> rebuild(@PathVariable Long documentId) {
        log.info("Re-vectorize knowledge document, documentId={}", documentId);
        return ApiResponse.ok(knowledgeService.vectorize(documentId, true));
    }
}
