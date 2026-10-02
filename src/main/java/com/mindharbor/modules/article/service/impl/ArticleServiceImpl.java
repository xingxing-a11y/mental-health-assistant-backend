package com.mindharbor.modules.article.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.common.cache.CacheKeys;
import com.mindharbor.common.cache.CacheProperties;
import com.mindharbor.common.cache.CacheService;
import com.mindharbor.modules.admin.entity.AdminOperationLog;
import com.mindharbor.modules.admin.service.AdminOperationLogService;
import com.mindharbor.modules.article.dto.ArticleRequest;
import com.mindharbor.modules.article.entity.Article;
import com.mindharbor.modules.article.mapper.ArticleMapper;
import com.mindharbor.modules.article.service.ArticleService;
import com.mindharbor.modules.article.vo.ArticleCacheDTO;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.security.CurrentUserProvider;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleServiceImpl implements ArticleService {

    private static final TypeReference<List<ArticleCacheDTO>> LIST_TYPE = new TypeReference<>() { };

    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AdminOperationLogService adminOperationLogService;
    private final CacheService cacheService;
    private final CacheProperties cacheProperties;

    public ArticleServiceImpl(
            ArticleMapper articleMapper,
            UserMapper userMapper,
            CurrentUserProvider currentUserProvider,
            AdminOperationLogService adminOperationLogService,
            CacheService cacheService,
            CacheProperties cacheProperties) {
        this.articleMapper = articleMapper;
        this.userMapper = userMapper;
        this.currentUserProvider = currentUserProvider;
        this.adminOperationLogService = adminOperationLogService;
        this.cacheService = cacheService;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public List<Article> list() {
        List<Article> cached = readListCache();
        if (cached != null) {
            return cached;
        }
        return cacheService.loadWithSingleFlight(CacheKeys.ARTICLE_LIST, this::readListCache, () -> {
            // 与关键词搜索、管理端列表保持同样的排序，避免缓存重建后顺序变化。
            List<Article> articles = articleMapper.findAllByOrderByUpdatedAtDesc();
            cacheService.put(CacheKeys.ARTICLE_LIST, articles.stream().map(ArticleCacheDTO::from).toList(),
                    cacheProperties.article().ttlSeconds(), cacheProperties.article().jitterSeconds());
            return articles;
        });
    }

    /** 读取列表缓存：未命中或缓存的 JSON 已损坏时返回 null，交给单飞回源决定是否查库。 */
    private List<Article> readListCache() {
        List<ArticleCacheDTO> cached = cacheService.get(CacheKeys.ARTICLE_LIST, LIST_TYPE);
        return cached == null ? null : cached.stream().map(ArticleCacheDTO::toEntity).toList();
    }

    @Override
    public List<Article> list(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return list();
        }

        return articleMapper.findByTitleContainingIgnoreCaseOrderByUpdatedAtDesc(keyword.trim());
    }

    @Override
    public Article get(Long id) {
        String detailKey = CacheKeys.articleDetail(id);
        String missingKey = CacheKeys.articleMissing(id);
        Article cached = readDetailCache(detailKey);
        if (cached != null) {
            return cached;
        }
        if (cacheService.exists(missingKey)) {
            throw new ResourceNotFoundException("文章不存在");
        }
        return cacheService.loadWithSingleFlight(
                detailKey,
                () -> readDetailCache(detailKey),
                () -> loadDetailFromDatabase(id, detailKey, missingKey));
    }

    /** 读取详情缓存；不存在的文章由空缓存键单独判断，不写入这里。 */
    private Article readDetailCache(String detailKey) {
        ArticleCacheDTO cached = cacheService.get(detailKey, ArticleCacheDTO.class);
        return cached == null ? null : cached.toEntity();
    }

    /** 回源详情：数据不存在时写入短 TTL 空缓存，拦截持续查询不存在编号的请求。 */
    private Article loadDetailFromDatabase(Long id, String detailKey, String missingKey) {
        if (cacheService.exists(missingKey)) {
            throw new ResourceNotFoundException("文章不存在");
        }
        Article article = articleMapper.findById(id).orElse(null);
        if (article == null) {
            cacheService.put(missingKey, Boolean.TRUE,
                    cacheProperties.article().missingTtlSeconds(),
                    cacheProperties.article().missingJitterSeconds());
            throw new ResourceNotFoundException("文章不存在");
        }
        cacheService.put(detailKey, ArticleCacheDTO.from(article),
                cacheProperties.article().ttlSeconds(), cacheProperties.article().jitterSeconds());
        return article;
    }

    @Override
    public List<Article> adminList() {
        return articleMapper.findAllByOrderByUpdatedAtDesc();
    }

    @Override
    @Transactional
    public Article create(ArticleRequest request) {
        Article article = new Article();
        apply(article, request);
        article.setUpdatedBy(currentUserName());
        articleMapper.insert(article);
        cacheService.deleteAfterCommit(CacheKeys.ARTICLE_LIST, CacheKeys.articleMissing(article.getId()));
        adminOperationLogService.record(
                AdminOperationLog.Type.ARTICLE,
                "article",
                article.getId(),
                "新增科普文章");
        return article;
    }

    @Override
    @Transactional
    public Article update(Long id, ArticleRequest request) {
        Article article = articleMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("文章不存在"));
        apply(article, request);
        article.setUpdatedAt(java.time.Instant.now());
        article.setUpdatedBy(currentUserName());
        articleMapper.updateById(article);
        cacheService.deleteAfterCommit(CacheKeys.ARTICLE_LIST, CacheKeys.articleDetail(article.getId()));
        adminOperationLogService.record(
                AdminOperationLog.Type.ARTICLE,
                "article",
                article.getId(),
                "编辑科普文章");
        return article;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        articleMapper.deleteById(id);
        cacheService.deleteAfterCommit(
                CacheKeys.ARTICLE_LIST,
                CacheKeys.articleDetail(id),
                CacheKeys.articleMissing(id));
        adminOperationLogService.record(AdminOperationLog.Type.ARTICLE, "article", id, "删除科普文章");
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
