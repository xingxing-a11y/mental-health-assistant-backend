package com.mindharbor.modules.article;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindharbor.modules.article.service.impl.ArticleServiceImpl;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 文章正文白名单清洗的行为约束。
 * 前台以 v-html 渲染正文，这层清洗是存储型 XSS 的唯一拦截点，必须逐条钉死。
 */
class ArticleContentSanitizeTest {

    private ArticleServiceImpl service;
    private Method sanitize;

    @BeforeEach
    void setUp() throws Exception {
        // 清洗逻辑只用静态白名单，不碰任何注入字段，构造器传 null 即可
        service = new ArticleServiceImpl(null, null, null, null, null, null);
        sanitize = ArticleServiceImpl.class.getDeclaredMethod("sanitizeContent", String.class);
        sanitize.setAccessible(true);
    }

    private String clean(String raw) throws Exception {
        return (String) sanitize.invoke(service, raw);
    }

    @Test
    void stripsScriptTag() throws Exception {
        String out = clean("<p>正文</p><script>alert(1)</script>");
        assertThat(out).contains("正文").doesNotContain("script").doesNotContain("alert");
    }

    @Test
    void keepsFontSizeStyle() throws Exception {
        String out = clean("<p><span style=\"font-size: 20px;\">大字</span></p>");
        assertThat(out).contains("font-size").contains("20px").contains("大字");
    }

    @Test
    void dropsUrlInsideStyle() throws Exception {
        String out = clean(
                "<p><span style=\"background: url(javascript:alert(1)); font-size: 18px;\">x</span></p>");
        assertThat(out).doesNotContain("url(").doesNotContain("javascript").contains("font-size");
    }

    @Test
    void removesImageTagWithHandler() throws Exception {
        String out = clean("<p>前<img src=x onerror=alert(1)>后</p>");
        assertThat(out).doesNotContain("img").doesNotContain("onerror").contains("前").contains("后");
    }

    @Test
    void rejectsJavascriptHref() throws Exception {
        String out = clean("<p><a href=\"javascript:alert(1)\">点我</a></p>");
        assertThat(out).doesNotContain("javascript:").contains("点我");
    }

    @Test
    void keepsPlainTextUntouched() throws Exception {
        String out = clean("第一段\n第二段");
        assertThat(out).contains("第一段").contains("第二段");
    }

    @Test
    void keepsHeadingsAndLists() throws Exception {
        String out = clean("<h3>小标题</h3><ul><li>条目一</li></ul>");
        assertThat(out).contains("<h3>").contains("<li>").contains("小标题");
    }

    @Test
    void blankContentStaysBlank() throws Exception {
        assertThat(clean("   ")).isEqualTo("   ");
        assertThat(clean(null)).isNull();
    }
}
