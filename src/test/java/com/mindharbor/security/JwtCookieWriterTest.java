package com.mindharbor.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.ServletContext;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/**
 * 认证 Cookie 的写入契约：名字、作用域、存活期。
 *
 * <p>这里守的是三件「改错了不会报错、只会让所有人登录后立即掉线」的事：
 * Cookie 名必须与解析端一致、作用域必须跟着 context-path 走、
 * 清除必须同时给空值与 Max-Age=0 且作用域与写入时相同。
 * 另外固定住 ServletContext 可作为构造参数被容器解析 —— 它是本类唯一的容器依赖。
 */
@DisplayName("认证 Cookie 写入")
class JwtCookieWriterTest {

    private static final long MAX_AGE_SECONDS = 7200L;

    @Test
    @DisplayName("下发令牌时写入含完整属性的 Set-Cookie")
    void writePutsFullCookieAttributes() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writerWithContextPath("/api/v1").write(response, "token-abc");

        String header = response.getHeader("Set-Cookie");
        assertNotNull(header, "先确认 Set-Cookie 真的写进去了，否则后续断言在空值上失去意义");
        assertTrue(header.startsWith("mh_token=token-abc"), header);
        assertTrue(header.contains("Path=/api/v1"), header);
        assertTrue(header.contains("Max-Age=" + MAX_AGE_SECONDS), header);
        assertTrue(header.contains("HttpOnly"), header);
        assertTrue(header.contains("SameSite=Lax"), header);
    }

    @Test
    @DisplayName("清除时写空值 + Max-Age=0，且作用域与写入时一致")
    void clearUsesZeroMaxAge() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writerWithContextPath("/api/v1").clear(response);

        String header = response.getHeader("Set-Cookie");
        assertNotNull(header, "清除同样要写 Set-Cookie，否则浏览器不会删除旧的登录凭证");
        assertTrue(header.startsWith("mh_token=;"), header);
        assertTrue(header.contains("Max-Age=0"), header);
        assertTrue(header.contains("Path=/api/v1"), header);
    }

    @Test
    @DisplayName("Cookie 作用域跟随 context-path，不再与配置手工同步")
    void pathFollowsContextPath() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writerWithContextPath("/api/v2").write(response, "token-abc");

        String header = response.getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.contains("Path=/api/v2"), header);
        assertFalse(header.contains("Path=/api/v1"),
                "作用域若写死，改了 context-path 后浏览器不再回传 Cookie，且没有任何报错");
    }

    @Test
    @DisplayName("context-path 为空时退回根路径，不写出非法的空作用域")
    void blankContextPathFallsBackToRoot() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writerWithContextPath("").write(response, "token-abc");

        String header = response.getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.contains("Path=/;"), header);
        assertFalse(header.contains("Path=;"),
                "空串不是合法的 Cookie Path，浏览器会忽略整条 Cookie");
    }

    @Test
    @DisplayName("容器能把 ServletContext 解析成构造参数")
    void containerInjectsServletContext() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(JwtCookieWriter.class, PropertySourcesPlaceholderConfigurer.class);
        try {
            context.refresh();
            assertNotNull(context.getBean(JwtCookieWriter.class),
                    "ServletContext 若不能作为构造参数解析，应用启动阶段就会失败");
        } finally {
            context.close();
        }
    }

    private static JwtCookieWriter writerWithContextPath(String contextPath) {
        return new JwtCookieWriter(servletContext(contextPath), false, MAX_AGE_SECONDS);
    }

    /** 只实现 getContextPath，其余方法返回 null；不用 Mockito，与项目既有假实现写法一致。 */
    private static ServletContext servletContext(String contextPath) {
        return (ServletContext) Proxy.newProxyInstance(
                JwtCookieWriterTest.class.getClassLoader(),
                new Class<?>[]{ServletContext.class},
                (proxy, method, arguments) -> {
                    if ("getContextPath".equals(method.getName())) {
                        return contextPath;
                    }
                    if ("toString".equals(method.getName())) {
                        return "ServletContext(contextPath=" + contextPath + ")";
                    }
                    return null;
                });
    }
}
