package com.mindharbor.modules.article.service.impl;

import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.modules.admin.entity.AdminOperationLog;
import com.mindharbor.modules.admin.service.AdminOperationLogService;
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
    private final AdminOperationLogService operationLogService;

    public ArticleServiceImpl(
            ArticleMapper mapper,
            UserMapper userMapper,
            CurrentUserProvider currentUserProvider,
            AdminOperationLogService operationLogService) {
        this.mapper = mapper;
        this.userMapper = userMapper;
        this.currentUserProvider = currentUserProvider;
        this.operationLogService = operationLogService;
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
        return mapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("文章不存在"));
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
        operationLogService.record(
                AdminOperationLog.Type.ARTICLE,
                "article",
                article.getId(),
                "新增科普文章");
        return article;
    }

    @Override
    public Article update(Long id, ArticleRequest request) {
        Article article = mapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("文章不存在"));
        apply(article, request);
        article.setUpdatedAt(java.time.Instant.now());
        article.setUpdatedBy(currentUserName());
        mapper.updateById(article);
        operationLogService.record(
                AdminOperationLog.Type.ARTICLE,
                "article",
                article.getId(),
                "编辑科普文章");
        return article;
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
        operationLogService.record(AdminOperationLog.Type.ARTICLE, "article", id, "删除科普文章");
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
