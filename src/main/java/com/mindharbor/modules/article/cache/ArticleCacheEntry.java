package com.mindharbor.modules.article.cache;

import com.mindharbor.modules.article.entity.Article;
import java.time.Instant;

/** 文章缓存快照：列表与详情共用，字段清单固定，实体增删字段不会让已写入的缓存无法反序列化。 */
public record ArticleCacheEntry(Long id, String title, String summary, String content,
                                Instant updatedAt, String updatedBy) {
    public static ArticleCacheEntry from(Article article) {
        return new ArticleCacheEntry(article.getId(), article.getTitle(), article.getSummary(),
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
