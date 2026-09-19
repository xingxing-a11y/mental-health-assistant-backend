package com.mindharbor.modules.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.common.storage.AvatarContent;
import com.mindharbor.common.storage.AvatarStorage;
import com.mindharbor.modules.user.service.UserService;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 头像读取接口：映射地址、响应形态与响应头。
 *
 * <p>这个接口与其它接口不同源——成功时是二进制流，失败时必须也是空体，否则
 * {@code <img src>} 收到 JSON 只会渲染出破图。这类契约靠肉眼看代码很难守住，因此固定成用例。
 */
@DisplayName("头像读取接口")
class AvatarControllerTest {

    @Test
    @DisplayName("映射模板与路径变量名逐字一致（依赖编译期 -parameters）")
    void mappingMatchesUrlPrefixAndParameterName() throws Exception {
        Method method = AvatarController.class.getMethod("avatar", String.class);

        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertEquals(AvatarStorage.URL_PREFIX + "/{filename}", mapping.value()[0],
                "读取地址必须与 users.avatar_url 共用同一常量，避免两处漂移");

        Parameter parameter = method.getParameters()[0];
        assertEquals("filename", parameter.getName(),
                "@PathVariable 未写显式名，变量名必须与映射模板一致，否则 Spring 启动即绑定失败");
    }

    @Test
    @DisplayName("返回类型是通配的 ResponseEntity，以同时容纳二进制流与空体")
    void returnTypeIsWildcardResponseEntity() throws Exception {
        Method method = AvatarController.class.getMethod("avatar", String.class);

        assertEquals(ResponseEntity.class, method.getReturnType(),
                "声明成具体泛型就无法同时表达「200 图片流」和「404 空体」");
    }

    @Test
    @DisplayName("命中头像时返回二进制流，并带缓存、inline 与 nosniff 头")
    void presentAvatarIsServedAsBinaryStream() {
        byte[] payload = "fake-png-payload".getBytes(StandardCharsets.UTF_8);
        AvatarController controller = new AvatarController(serviceReturning(
                new AvatarContent(new ByteArrayInputStream(payload), payload.length, MediaType.IMAGE_PNG)));

        ResponseEntity<?> response = controller.avatar("avatar.png");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        assertEquals(payload.length, response.getHeaders().getContentLength());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"),
                "不依赖上游拦截器的响应头是否已生效");
        assertTrue(String.valueOf(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                        .startsWith("inline"),
                "缺 inline 时部分浏览器会对图片弹下载框");
        assertTrue(String.valueOf(response.getHeaders().getCacheControl()).contains("private"),
                "头像只对登录用户开放，缓存不能跨用户共享");
    }

    @Test
    @DisplayName("头像缺失时收敛为 404 空体，而不是让异常冒泡成 JSON")
    void missingAvatarBecomesEmpty404() {
        AvatarController controller = new AvatarController(serviceWithoutAvatar());

        ResponseEntity<?> response = controller.avatar("not-a-uuid.png");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
                "异常若冒泡到 GlobalExceptionHandler，响应体是 JSON，浏览器只会渲染出破图");
        assertNull(response.getBody(), "404 必须是空体，让前端直接走 onerror 分支");
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
    }

    /** UserService 是接口且方法很多，用动态代理只实现 loadAvatar 这一条。 */
    private static UserService serviceReturning(AvatarContent content) {
        return (UserService) Proxy.newProxyInstance(
                AvatarControllerTest.class.getClassLoader(),
                new Class<?>[] {UserService.class},
                (proxy, method, args) -> {
                    if ("loadAvatar".equals(method.getName())) {
                        return content;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static UserService serviceWithoutAvatar() {
        return (UserService) Proxy.newProxyInstance(
                AvatarControllerTest.class.getClassLoader(),
                new Class<?>[] {UserService.class},
                (proxy, method, args) -> {
                    if ("loadAvatar".equals(method.getName())) {
                        throw new ResourceNotFoundException("头像不存在");
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
