package com.hosunton.backend.global.security.jwt;

import com.hosunton.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");
    private static final JwtProperties PROPERTIES =
            new JwtProperties("test-secret-key-must-be-at-least-32-bytes!!", Duration.ofHours(1), Duration.ofDays(14));

    private JwtProvider providerAt(Instant instant) {
        return new JwtProvider(PROPERTIES, Clock.fixed(instant, ZoneId.of("Asia/Seoul")));
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 유효한_토큰이면_SecurityContext에_userId를_넣는다() throws Exception {
        JwtProvider provider = providerAt(NOW);
        String token = provider.createAccessToken(1L).value();
        MockFilterChain chain = new MockFilterChain();

        new JwtAuthenticationFilter(provider).doFilter(requestWithToken(token), new MockHttpServletResponse(), chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
        assertThat(chain.getRequest()).isNotNull(); // 다음 필터로 넘어갔는지
    }

    @Test
    void 헤더가_없으면_인증하지_않고_다음_필터로_넘긴다() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        new JwtAuthenticationFilter(providerAt(NOW))
                .doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void 만료된_토큰이면_인증하지_않고_TOKEN_EXPIRED를_남긴다() throws Exception {
        String token = providerAt(NOW).createAccessToken(1L).value();
        MockHttpServletRequest request = requestWithToken(token);
        MockFilterChain chain = new MockFilterChain();

        new JwtAuthenticationFilter(providerAt(NOW.plus(Duration.ofHours(2))))
                .doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE)).isEqualTo(ErrorCode.TOKEN_EXPIRED);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void 위조된_토큰이면_인증하지_않고_UNAUTHORIZED를_남긴다() throws Exception {
        MockHttpServletRequest request = requestWithToken("not-a-jwt");

        new JwtAuthenticationFilter(providerAt(NOW))
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE)).isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}