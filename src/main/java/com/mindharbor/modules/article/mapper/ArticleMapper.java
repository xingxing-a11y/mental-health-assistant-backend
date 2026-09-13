package com.mindharbor.modules.article.mapper;

import com.mindharbor.modules.article.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ArticleMapper extends JpaRepository<Article, Long> {

    List<Article> findByTitleContainingIgnoreCaseOrderByUpdatedAtDesc(String keyword);

    List<Article> findAllByOrderByUpdatedAtDesc();
}

