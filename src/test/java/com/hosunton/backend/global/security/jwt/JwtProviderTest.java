package com.hosunton.backend.global.security.jwt;

import com.hosunton.backend.global.exception.BusinessException;
import com.hosunton.backend.global.exception.ErrorCode;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes!!";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");

    private final JwtProperties properties =
            new JwtProperties(SECRET, Duration.ofHours(1), Duration.ofDays(14));

    private JwtProvider providerAt(Instant instant) {
        return new JwtProvider(properties, Clock.fixed(instant, KST));
    }

    @Test
    void Access_Token을_발급하고_userId를_꺼낸다() {
        JwtProvider provider = providerAt(NOW);

        IssuedToken token = provider.createAccessToken(1L);

        assertThat(provider.parseAccessToken(token.value())).isEqualTo(1L);
        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void Refresh_Token을_발급하고_userId를_꺼낸다() {
        JwtProvider provider = providerAt(NOW);

        IssuedToken token = provider.createRefreshToken(1L);

        assertThat(provider.parseRefreshToken(token.value())).isEqualTo(1L);
        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
    }

    @Test
    void 만료된_Access_Token은_TOKEN_EXPIRED() {
        String token = providerAt(NOW).createAccessToken(1L).value();
        JwtProvider twoHoursLater = providerAt(NOW.plus(Duration.ofHours(2)));

        assertThatThrownBy(() -> twoHoursLater.parseAccessToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void 위조된_Access_Token은_UNAUTHORIZED() {
        JwtProvider provider = providerAt(NOW);
        String token = provider.createAccessToken(1L).value();
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> provider.parseAccessToken(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 다른_키로_서명된_토큰은_UNAUTHORIZED() {
        JwtProvider other = new JwtProvider(
                new JwtProperties("another-secret-key-also-at-least-32-bytes!", Duration.ofHours(1), Duration.ofDays(14)),
                Clock.fixed(NOW, KST));
        String token = other.createAccessToken(1L).value();

        assertThatThrownBy(() -> providerAt(NOW).parseAccessToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 형식이_잘못된_토큰은_UNAUTHORIZED() {
        assertThatThrownBy(() -> providerAt(NOW).parseAccessToken("not-a-jwt"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void Refresh_Token을_Access_Token으로_쓰면_UNAUTHORIZED() {
        JwtProvider provider = providerAt(NOW);
        String refresh = provider.createRefreshToken(1L).value();

        assertThatThrownBy(() -> provider.parseAccessToken(refresh))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void Access_Token을_Refresh_Token으로_쓰면_INVALID_REFRESH_TOKEN() {
        JwtProvider provider = providerAt(NOW);
        String access = provider.createAccessToken(1L).value();

        assertThatThrownBy(() -> provider.parseRefreshToken(access))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void 만료된_Refresh_Token은_INVALID_REFRESH_TOKEN() {
        String token = providerAt(NOW).createRefreshToken(1L).value();
        JwtProvider fifteenDaysLater = providerAt(NOW.plus(Duration.ofDays(15)));

        assertThatThrownBy(() -> fifteenDaysLater.parseRefreshToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void 같은_사용자에게_같은_시각에_발급해도_Refresh_Token은_매번_다르다() {
        JwtProvider provider = providerAt(NOW);

        String first = provider.createRefreshToken(1L).value();
        String second = provider.createRefreshToken(1L).value();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void 키가_32바이트보다_짧으면_생성에_실패한다() {
        JwtProperties weak = new JwtProperties("short-key", Duration.ofHours(1), Duration.ofDays(14));

        assertThatThrownBy(() -> new JwtProvider(weak, Clock.fixed(NOW, KST)))
                .isInstanceOf(WeakKeyException.class);
    }
}