package com.mindharbor.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;
    private final long expirationSeconds;

    public JwtTokenService(
            @Value("${security.jwt.secret:change-this-development-secret}") String secret,
            @Value("${security.jwt.expiration-seconds:7200}") long expirationSeconds) {
        if (secret == null
                || secret.isBlank()
                || "change-this-development-secret".equals(secret)
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET 必须配置为至少 32 字节的随机密钥");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION_SECONDS 必须大于 0");
        }
        this.secret = secret;
        this.expirationSeconds = expirationSeconds;
    }

    public String createToken(Long userId, String username, String role) {
        long expiresAt = Instant.now().getEpochSecond() + expirationSeconds;
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode(String.format(
                "{\"sub\":\"%s\",\"username\":\"%s\",\"role\":\"%s\",\"exp\":%d}",
                userId,
                username,
                role,
                expiresAt));
        return header + "." + payload + "." + sign(header + "." + payload);
    }

    public boolean isValid(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3
                    || parts[0].isBlank()
                    || parts[1].isBlank()
                    || parts[2].isBlank()
                    || !isHs256Header(parts[0])
                    || !MessageDigest.isEqual(
                            sign(parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII),
                            parts[2].getBytes(StandardCharsets.US_ASCII))) {
                return false;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            long expiration = Long.parseLong(payload.replaceAll(".*\\\"exp\\\":(\\d+).*", "$1"));
            return expiration > Instant.now().getEpochSecond();
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean isHs256Header(String encodedHeader) {
        String header = new String(
                Base64.getUrlDecoder().decode(encodedHeader),
                StandardCharsets.UTF_8);
        return "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".equals(header);
    }

    public String getRole(String token) {
        try {
            String[] parts = token.split("\\.");
            String payload = new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8);
            return payload.replaceAll(".*\\\"role\\\":\\\"([^\"]+)\\\".*", "$1");
        } catch (Exception exception) {
            return "";
        }
    }

    public Long getUserId(String token) {
        try {
            String[] parts = token.split("\\.");
            String payload = new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8);
            String subject = payload.replaceAll(".*\\\"sub\\\":\\\"([^\\\"]+)\\\".*", "$1");
            return Long.valueOf(subject);
        } catch (Exception exception) {
            throw new IllegalArgumentException("JWT 用户身份无效", exception);
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT 签名失败", exception);
        }
    }
}
