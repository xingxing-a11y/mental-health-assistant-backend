package com.mindharbor.common.cache;

/**
 * 缓存键统一入口。
 *
 * <p>键名集中在这里，保证写入与失效使用同一份定义，也让键值与文档中的缓存表一一对应。
 * 所有键都带 {@code mindharbor:cache:} 前缀，便于与同一 Redis 实例上的其他数据区分。
 */
public final class CacheKeys {

    /** 科普文章列表：公共列表接口，关键词搜索不缓存。 */
    public static final String ARTICLE_LIST = "mindharbor:cache:article:list";

    /** 系统公告列表。 */
    public static final String ANNOUNCEMENT_LIST = "mindharbor:cache:announcement:list";

    /** 启用中的敏感词，供对话敏感校验读取。 */
    public static final String SENSITIVE_WORD_ENABLED = "mindharbor:cache:sensitive-word:enabled";

    /** 完整敏感词词库，供管理后台列表读取。 */
    public static final String SENSITIVE_WORD_ALL = "mindharbor:cache:sensitive-word:all";

    private static final String ARTICLE_DETAIL_PREFIX = "mindharbor:cache:article:";

    private static final String ARTICLE_MISSING_PREFIX = "mindharbor:cache:article:missing:";

    private CacheKeys() {
    }

    /** 科普文章详情键。 */
    public static String articleDetail(Long articleId) {
        return ARTICLE_DETAIL_PREFIX + articleId;
    }

    /** 不存在的科普文章空缓存键，用于拦截持续查询不存在编号的请求。 */
    public static String articleMissing(Long articleId) {
        return ARTICLE_MISSING_PREFIX + articleId;
    }
}
