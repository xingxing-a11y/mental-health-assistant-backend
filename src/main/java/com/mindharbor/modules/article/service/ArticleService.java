package com.mindharbor.modules.article.service;

import com.mindharbor.modules.article.entity.Article;
import java.util.List;
import com.mindharbor.modules.article.dto.ArticleRequest;

public interface ArticleService {

    List<Article> list();

    List<Article> list(String keyword);

    Article get(Long id);

    List<Article> adminList();

    Article create(ArticleRequest request);

    Article update(Long id, ArticleRequest request);

    void delete(Long id);

}
