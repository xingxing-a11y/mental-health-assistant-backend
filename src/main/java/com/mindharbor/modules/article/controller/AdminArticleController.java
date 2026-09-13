package com.mindharbor.modules.article.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.article.dto.ArticleRequest;
import com.mindharbor.modules.article.service.ArticleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/articles")
public class AdminArticleController {
    private final ArticleService service;

    public AdminArticleController(ArticleService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<?> list() {
        return ApiResponse.ok(service.adminList());
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody ArticleRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody ArticleRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
