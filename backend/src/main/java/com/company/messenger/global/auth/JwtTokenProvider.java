package com.company.messenger.global.auth;

import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final AuthProperties authProperties;

    public JwtTokenProvider(
            @Value("${app.security.jwt-secret}") String jwtSecret,
            AuthProperties authProperties
    ) {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.authProperties = authProperties;
    }

    public String createAccessToken(String userId, String sessionId) {
        return createToken(userId, sessionId, TokenType.ACCESS, authProperties.accessTokenExpiration().toSeconds());
    }

    public String createRefreshToken(String userId, String sessionId) {
        return createToken(userId, sessionId, TokenType.REFRESH, authProperties.refreshTokenExpiration().toSeconds());
    }

    public JwtTokenClaims parseAndValidate(String token, TokenType expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            TokenType tokenType = TokenType.valueOf(claims.get("type", String.class));
            if (tokenType != expectedType) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN_TYPE);
            }

            return new JwtTokenClaims(
                    claims.getSubject(),
                    claims.get("sid", String.class),
                    tokenType
            );
        } catch (IllegalArgumentException | JwtException exception) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    private String createToken(String userId, String sessionId, TokenType tokenType, long expiresInSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim("sid", sessionId)
                .claim("type", tokenType.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiresInSeconds)))
                .signWith(secretKey)
                .compact();
    }
}

