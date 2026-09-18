package com.mindharbor.modules.article.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.article.service.ArticleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 心理科普文章公开查询接口。 */
@RestController
@RequestMapping("/articles")
public class ArticleController {

    private static final Logger log = LoggerFactory.getLogger(ArticleController.class);

    private final ArticleService service;

    public ArticleController(ArticleService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) String keyword) {
        log.info("List public psychology articles");
        return ApiResponse.ok(service.list(keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable Long id) {
        log.info("Get psychology article, id={}", id);
        return ApiResponse.ok(service.get(id));
    }
}
