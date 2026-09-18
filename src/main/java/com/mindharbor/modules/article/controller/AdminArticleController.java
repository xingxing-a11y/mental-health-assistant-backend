package com.mindharbor.modules.article.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.article.dto.ArticleRequest;
import com.mindharbor.modules.article.service.ArticleService;
import jakarta.validation.Valid;
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

/** 管理员科普文章维护接口。 */
@RestController
@RequestMapping("/admin/articles")
public class AdminArticleController {

    private static final Logger log = LoggerFactory.getLogger(AdminArticleController.class);

    private final ArticleService service;

    public AdminArticleController(ArticleService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<?> list() {
        log.info("List articles for administrator");
        return ApiResponse.ok(service.adminList());
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody ArticleRequest request) {
        log.info("Create psychology article");
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(
            @PathVariable Long id,
            @Valid @RequestBody ArticleRequest request) {
        log.info("Update psychology article, articleId={}", id);
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        log.info("Delete psychology article, articleId={}", id);
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
