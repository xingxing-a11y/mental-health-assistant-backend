package com.mindharbor.modules.user.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.user.dto.LoginRequest;
import com.mindharbor.modules.user.dto.PasswordUpdateRequest;
import com.mindharbor.modules.user.dto.ProfileUpdateRequest;
import com.mindharbor.modules.user.dto.RegisterRequest;
import com.mindharbor.modules.user.service.UserService;
import com.mindharbor.modules.user.vo.AuthResultVO;
import com.mindharbor.modules.user.vo.UserVO;
import com.mindharbor.security.JwtTokenService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 认证与当前用户资料接口，令牌只通过 HttpOnly Cookie 下发，不在日志中输出。 */
@RestController
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private static final String TOKEN_COOKIE_NAME = "mh_token";

    private static final String TOKEN_COOKIE_PATH = "/api/v1";

    private final UserService service;
    private final JwtTokenService tokenService;
    private final boolean secureCookie;

    /** Cookie 有效期复用令牌过期时间，避免改了一处、另一处仍留旧值。 */
    private final long tokenCookieMaxAgeSeconds;

    public UserController(
            UserService service,
            JwtTokenService tokenService,
            @Value("${security.cookie.secure:false}") boolean secureCookie,
            @Value("${security.jwt.expiration-seconds:7200}") long tokenCookieMaxAgeSeconds) {
        this.service = service;
        this.tokenService = tokenService;
        this.secureCookie = secureCookie;
        this.tokenCookieMaxAgeSeconds = tokenCookieMaxAgeSeconds;
    }

    @PostMapping("/auth/register")
    public ApiResponse<AuthResultVO> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        log.info("Register endpoint called, username={}", request.getUsername());
        UserVO user = service.register(request);
        addAuthCookie(
                response,
                tokenService.createToken(user.getId(), user.getUsername(), user.getRole()));
        return ApiResponse.ok(new AuthResultVO(user));
    }

    @PostMapping("/auth/login")
    public ApiResponse<AuthResultVO> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        log.info("Login endpoint called, username={}", request.getUsername());
        UserVO user = service.login(request);
        addAuthCookie(
                response,
                tokenService.createToken(user.getId(), user.getUsername(), user.getRole()));
        return ApiResponse.ok(new AuthResultVO(user));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        log.info("Logout endpoint called");
        response.addHeader("Set-Cookie", authCookie("", 0).toString());
        return ApiResponse.ok(null);
    }

    @GetMapping("/users/me")
    public ApiResponse<UserVO> current() {
        log.info("Current profile endpoint called");
        return ApiResponse.ok(service.current());
    }

    @PutMapping("/users/me")
    public ApiResponse<UserVO> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        log.info("Update current user profile");
        return ApiResponse.ok(service.updateProfile(request));
    }

    @PostMapping("/users/me/avatar")
    public ApiResponse<UserVO> updateAvatar(@RequestPart("file") MultipartFile file) {
        log.info("Update current user avatar");
        return ApiResponse.ok(service.updateAvatar(file));
    }

    @PutMapping("/users/me/password")
    public ApiResponse<Void> updatePassword(@Valid @RequestBody PasswordUpdateRequest request) {
        log.info("Update current user password");
        service.updatePassword(request);
        return ApiResponse.ok(null);
    }

    /** 注销账号：业务数据匿名化保留，注销后立即清除登录 Cookie。 */
    @PostMapping("/users/me/deactivate")
    public ApiResponse<Void> deactivate(HttpServletResponse response) {
        log.info("Deactivate current account");
        service.deactivate();
        response.addHeader("Set-Cookie", authCookie("", 0).toString());
        return ApiResponse.ok(null);
    }

    private void addAuthCookie(HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", authCookie(token, tokenCookieMaxAgeSeconds).toString());
    }

    private ResponseCookie authCookie(String value, long maxAge) {
        return ResponseCookie.from(TOKEN_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path(TOKEN_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
