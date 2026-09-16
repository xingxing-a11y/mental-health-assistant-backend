package com.mindharbor.modules.user.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.user.dto.LoginRequest;
import com.mindharbor.modules.user.dto.RegisterRequest;
import com.mindharbor.modules.user.dto.PasswordUpdateRequest;
import com.mindharbor.modules.user.dto.ProfileUpdateRequest;
import com.mindharbor.modules.user.service.UserService;
import com.mindharbor.security.JwtTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService service;
    private final JwtTokenService tokenService;
    private final boolean secureCookie;

    public UserController(
            UserService service,
            JwtTokenService tokenService,
            @Value("${security.cookie.secure:false}") boolean secureCookie) {
        this.service = service;
        this.tokenService = tokenService;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/auth/register")

    public ApiResponse<?> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        log.info("Register endpoint called, username={}", request.getUsername());
        var user = service.register(request);
        addAuthCookie(response, tokenService.createToken(user.getId(), user.getUsername(), user.getRole()));
        return ApiResponse.ok(java.util.Map.of("user", user));
    }

    @PostMapping("/auth/login")

    public ApiResponse<?> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        log.info("Login endpoint called, username={}", request.getUsername());
        var user = service.login(request);
        addAuthCookie(response, tokenService.createToken(user.getId(), user.getUsername(), user.getRole()));
        return ApiResponse.ok(java.util.Map.of("user", user));
    }

    @PostMapping("/auth/logout")

    public ApiResponse<?> logout(HttpServletResponse response) {
        log.info("Logout endpoint called");
        response.addHeader("Set-Cookie", authCookie("", 0).toString());
        return ApiResponse.ok(null);
    }

    private void addAuthCookie(HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", authCookie(token, 7200).toString());
    }

    private ResponseCookie authCookie(String value, long maxAge) {
        return ResponseCookie.from("mh_token", value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api/v1")
                .maxAge(maxAge)
                .build();
    }

    @GetMapping("/users/me")

    public ApiResponse<?> current() {
        log.info("Current profile endpoint called");
        return ApiResponse.ok(service.current());
    }

    @PutMapping("/users/me")
    public ApiResponse<?> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        log.info("Update current user profile");
        return ApiResponse.ok(service.updateProfile(request));
    }

    @PostMapping("/users/me/avatar")
    public ApiResponse<?> updateAvatar(@RequestPart("file") MultipartFile file) {
        log.info("Update current user avatar");
        return ApiResponse.ok(service.updateAvatar(file));
    }

    @PutMapping("/users/me/password")
    public ApiResponse<?> updatePassword(@Valid @RequestBody PasswordUpdateRequest request) {
        log.info("Update current user password");
        service.updatePassword(request);
        return ApiResponse.ok(null);
    }
}

