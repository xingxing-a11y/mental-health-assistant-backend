package com.mindharbor.modules.article.service.impl;

import com.mindharbor.modules.article.entity.Article;
import com.mindharbor.modules.article.mapper.ArticleMapper;
import com.mindharbor.modules.article.service.ArticleService;
import java.util.List;
import org.springframework.stereotype.Service;
import com.mindharbor.modules.article.dto.ArticleRequest;

@Service
public class ArticleServiceImpl implements ArticleService {

    private final ArticleMapper mapper;

    public ArticleServiceImpl(ArticleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Article> list() {
        return mapper.findAll();
    }

    @Override
    public List<Article> list(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return list();
        }

        return mapper.findByTitleContainingIgnoreCaseOrderByUpdatedAtDesc(keyword.trim());
    }

    @Override
    public Article get(Long id) {
        return mapper.findById(id).orElseThrow();
    }

    @Override
    public List<Article> adminList() {
        return mapper.findAllByOrderByUpdatedAtDesc();
    }

    @Override
    public Article create(ArticleRequest request) {
        Article article = new Article();
        apply(article, request);
        return mapper.save(article);
    }

    @Override
    public Article update(Long id, ArticleRequest request) {
        Article article = mapper.findById(id).orElseThrow();
        apply(article, request);
        article.setUpdatedAt(java.time.Instant.now());
        return mapper.save(article);
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    private void apply(Article article, ArticleRequest request) {
        article.setTitle(request.getTitle());
        article.setSummary(request.getSummary());
        article.setContent(request.getContent());
    }
}
