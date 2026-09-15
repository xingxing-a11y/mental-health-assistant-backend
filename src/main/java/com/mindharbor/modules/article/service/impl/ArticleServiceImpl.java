package com.mindharbor.modules.article.service.impl;

import com.mindharbor.modules.article.entity.Article;
import com.mindharbor.modules.article.mapper.ArticleMapper;
import com.mindharbor.modules.article.service.ArticleService;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.security.CurrentUserProvider;
import java.util.List;
import org.springframework.stereotype.Service;
import com.mindharbor.modules.article.dto.ArticleRequest;

@Service
public class ArticleServiceImpl implements ArticleService {

    private final ArticleMapper mapper;
    private final UserMapper userMapper;
    private final CurrentUserProvider currentUserProvider;

    public ArticleServiceImpl(
            ArticleMapper mapper,
            UserMapper userMapper,
            CurrentUserProvider currentUserProvider) {
        this.mapper = mapper;
        this.userMapper = userMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public List<Article> list() {
        return mapper.selectList(null);
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
        article.setUpdatedBy(currentUserName());
        mapper.insert(article);
        return article;
    }

    @Override
    public Article update(Long id, ArticleRequest request) {
        Article article = mapper.findById(id).orElseThrow();
        apply(article, request);
        article.setUpdatedAt(java.time.Instant.now());
        article.setUpdatedBy(currentUserName());
        mapper.updateById(article);
        return article;
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

    private String currentUserName() {
        User user = userMapper.findById(currentUserProvider.getUserId())
                .orElseThrow(() -> new IllegalStateException("当前用户不存在"));
        return user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername()
                : user.getNickname();
    }
}
