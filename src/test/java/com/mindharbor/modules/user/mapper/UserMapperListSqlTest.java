package com.mindharbor.modules.user.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 用户列表两条 SQL 的动态标签解析结果。
 *
 * <p>假 mapper 只能钉住 Java 侧的逻辑，钉不住 {@code <script>/<where>/<if>} 拼出来的 SQL。
 * 这里让 MyBatis 真的解析一遍注解内容，再断言生成的语句——标签写错（例如漏 {@code </if>}、
 * where 里多写一个 {@code AND}、占位符数量与参数不匹配）在这一层就能发现，不必等启动应用。
 *
 * <p>参数一律用 {@code Map} 传入，因为注解方法的参数本身就是 {@code @Param} 平铺的 Map。
 */
@DisplayName("用户列表 SQL 动态标签")
class UserMapperListSqlTest {

    @Test
    @DisplayName("关键词为空：不生成 WHERE，只保留排序与分页")
    void noKeywordProducesNoWhere() {
        String sql = render("pageByNickname", params(null, 20, 40L));

        assertEquals(
                "SELECT * FROM users ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", sql,
                "keyword 为 null 时必须完全跳过 WHERE，否则会拼出空条件导致语法错误");
    }

    @Test
    @DisplayName("关键词非空：生成 LIKE 条件，占位符数量与参数一致")
    void keywordProducesLike() {
        String sql = render("pageByNickname", params("小明", 20, 40L));

        assertEquals(
                "SELECT * FROM users WHERE nickname LIKE CONCAT('%', ?, '%') "
                        + "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", sql);
        assertTrue(sql.indexOf('?') < sql.indexOf("ORDER BY"), "LIKE 的占位符必须排在 LIMIT 之前");
    }

    @Test
    @DisplayName("统计语句：关键词为空时统计全表，非空时只统计匹配行")
    void countReflectsKeyword() {
        assertEquals("SELECT COUNT(*) FROM users", render("countByNickname", params(null, 0, 0L)));
        assertEquals(
                "SELECT COUNT(*) FROM users WHERE nickname LIKE CONCAT('%', ?, '%')",
                render("countByNickname", params("小明", 0, 0L)));
    }

    private static Map<String, Object> params(String keyword, int limit, long offset) {
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", keyword);
        params.put("limit", limit);
        params.put("offset", offset);
        return params;
    }

    /** 取注解里的脚本，交给 MyBatis 解析成 SQL；空白归一化后再断言。 */
    private static String render(String methodName, Map<String, Object> params) {
        Method method = Arrays.stream(UserMapper.class.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("找不到方法 " + methodName));
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        SqlSource sqlSource = new XMLLanguageDriver()
                .createSqlSource(new Configuration(), script, Map.class);
        return sqlSource.getBoundSql(params).getSql().replaceAll("\\s+", " ").trim();
    }
}
