package com.mindharbor.modules.user.controller;

import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.common.storage.AvatarContent;
import com.mindharbor.common.storage.AvatarStorage;
import com.mindharbor.modules.user.service.UserService;
import java.time.Duration;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 头像读取：无论头像存在本地磁盘还是对象存储，都从这个地址返回二进制内容。
 * 该地址受 JWT 保护，未登录返回 401，因此响应体不套 ApiResponse 结构。
 *
 * <p>返回值刻意声明为 {@code ResponseEntity<?>}：本接口成功时是二进制流，
 * 失败时是空体，两种形态无法用同一个具体类型表达。同时把
 * {@link ResourceNotFoundException} 在控制器内收敛成 404 空体，不让它冒泡到
 * {@code GlobalExceptionHandler}——后者的响应体是 JSON，与 {@code <img src>}
 * 期望的图片不兼容，浏览器只会渲染出一个破图（P1-6）。
 */
@RestController
public class AvatarController {

    private final UserService userService;

    public AvatarController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping(AvatarStorage.URL_PREFIX + "/{filename}")
    public ResponseEntity<?> avatar(@PathVariable String filename) {
        try {
            AvatarContent content = userService.loadAvatar(filename);
            return ResponseEntity.ok()
                    .contentType(content.contentType())
                    .contentLength(content.contentLength())
                    // 文件名是随机 UUID、内容不会再变化，可以放心缓存；头像只对登录用户开放，因此不加 public。
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                    // 显式 inline：避免浏览器对未知类型弹下载框（P1-2）。
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + filename + "\"")
                    // 显式声明 nosniff，不依赖上游 HeaderWriter 是否覆盖本响应（P1-3）。
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new InputStreamResource(content.content()));
        } catch (ResourceNotFoundException exception) {
            // 头像缺失是可预期的正常情况（用户没设头像、刚被清理），
            // 返回空体而非 JSON，让前端直接走 onerror 分支。
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("X-Content-Type-Options", "nosniff")
                    .build();
        }
    }
}
