package com.mindharbor.modules.knowledge.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.knowledge.dto.KnowledgeDocumentRequest;
import com.mindharbor.modules.knowledge.service.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin/knowledge-documents")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @GetMapping

    public ApiResponse<?> list() {
        log.info("List knowledge documents");
        return ApiResponse.ok(service.list());
    }

    @PostMapping

    public ApiResponse<?> create(@Valid @RequestBody KnowledgeDocumentRequest request) {
        log.info("Create knowledge document");
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")

    public ApiResponse<?> update(
            @PathVariable Long id,
            @Valid @RequestBody KnowledgeDocumentRequest request) {
        log.info("Update knowledge document, documentId={}", id);
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")

    public ApiResponse<?> delete(@PathVariable Long id) {
        log.info("Delete knowledge document, documentId={}", id);
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/vectorize")

    public ApiResponse<?> vectorize(@PathVariable Long id) {
        log.info("Vectorize knowledge document, documentId={}", id);
        return ApiResponse.ok(service.vectorize(id, false));
    }

    @PostMapping("/{id}/re-vectorize")

    public ApiResponse<?> rebuild(@PathVariable Long id) {
        log.info("Re-vectorize knowledge document, documentId={}", id);
        return ApiResponse.ok(service.vectorize(id, true));
    }
}


