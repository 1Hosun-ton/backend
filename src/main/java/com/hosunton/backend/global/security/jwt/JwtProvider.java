package com.hosunton.backend.global.security.jwt;

import com.hosunton.backend.global.exception.BusinessException;
import com.hosunton.backend.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey key;
    private final JwtParser parser;

    public JwtProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public IssuedToken createAccessToken(Long userId) {
        return create(userId, ACCESS, properties.accessTokenExpiration().toMillis(), null);
    }

    public IssuedToken createRefreshToken(Long userId) {
        return create(userId, REFRESH, properties.refreshTokenExpiration().toMillis(), UUID.randomUUID().toString());
    }

    public Long parseAccessToken(String token) {
        try {
            Claims claims = parse(token);
            if (!ACCESS.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            return Long.valueOf(claims.getSubject());
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    public Long parseRefreshToken(String token) {
        try {
            Claims claims = parse(token);
            if (!REFRESH.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
            }
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private IssuedToken create(Long userId, String type, long validityMillis, String jti) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusMillis(validityMillis);
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, type)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    private Claims parse(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }
}