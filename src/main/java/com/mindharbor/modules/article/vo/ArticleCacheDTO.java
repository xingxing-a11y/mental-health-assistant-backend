package com.mindharbor.modules.article.vo;

import com.mindharbor.modules.article.entity.Article;
import java.time.Instant;

public record ArticleCacheDTO(Long id, String title, String summary, String content,
                              Instant updatedAt, String updatedBy) {
    public static ArticleCacheDTO from(Article article) {
        return new ArticleCacheDTO(article.getId(), article.getTitle(), article.getSummary(),
                article.getContent(), article.getUpdatedAt(), article.getUpdatedBy());
    }

    public Article toEntity() {
        Article article = new Article();
        article.setId(id);
        article.setTitle(title);
        article.setSummary(summary);
        article.setContent(content);
        article.setUpdatedAt(updatedAt);
        article.setUpdatedBy(updatedBy);
        return article;
    }
}
