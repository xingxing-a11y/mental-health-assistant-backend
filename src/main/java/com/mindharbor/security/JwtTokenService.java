package com.mindharbor.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 使用 Java JWT 生成和校验签名令牌，不在业务代码中手工拼接 JWT。 */
@Service
public class JwtTokenService {

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final long expirationSeconds;

    public JwtTokenService(
            @Value("${security.jwt.secret:}") String secret,
            @Value("${security.jwt.expiration-seconds:7200}") long expirationSeconds) {
        if (secret == null
                || secret.isBlank()
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET 必须配置为至少 32 字节的随机密钥");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION_SECONDS 必须大于 0");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).withIssuer("mindharbor").build();
        this.expirationSeconds = expirationSeconds;
    }

    public String createToken(Long userId, String username, String role) {
        Date expiresAt = new Date(System.currentTimeMillis() + expirationSeconds * 1000L);
        return JWT.create()
                .withIssuer("mindharbor")
                .withSubject(String.valueOf(userId))
                .withClaim("username", username)
                .withClaim("role", role)
                .withExpiresAt(expiresAt)
                .sign(algorithm);
    }

    public boolean isValid(String token) {
        try {
            verifier.verify(token);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public String getRole(String token) {
        return verify(token).getClaim("role").asString();
    }

    public Long getUserId(String token) {
        try {
            return Long.valueOf(verify(token).getSubject());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("JWT 用户身份无效", exception);
        }
    }

    private DecodedJWT verify(String token) {
        return verifier.verify(token);
    }
}
