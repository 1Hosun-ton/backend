package com.hosunton.backend.domain.auth;

import com.hosunton.backend.domain.auth.dto.AuthResponse;
import com.hosunton.backend.domain.auth.dto.SignupRequest;
import com.hosunton.backend.domain.user.User;
import com.hosunton.backend.domain.user.UserRepository;
import com.hosunton.backend.global.exception.BusinessException;
import com.hosunton.backend.global.exception.ErrorCode;
import com.hosunton.backend.global.security.jwt.JwtProperties;
import com.hosunton.backend.global.security.jwt.JwtProvider;
import com.hosunton.backend.global.security.jwt.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
    private JwtProvider jwtProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(
                new JwtProperties("test-secret-key-must-be-at-least-32-bytes!!", Duration.ofHours(1), Duration.ofDays(14)),
                clock);
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtProvider, clock);
    }

    private void givenSavedUserGetsId(long id) {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", id);
            return user;
        });
    }

    @Test
    void 회원가입하면_토큰과_사용자_정보를_반환한다() {
        when(userRepository.existsByEmail("harin@example.com")).thenReturn(false);
        givenSavedUserGetsId(1L);

        AuthResponse response = authService.signup(new SignupRequest("하린", "harin@example.com", "123456"));

        assertThat(jwtProvider.parseAccessToken(response.accessToken())).isEqualTo(1L);
        assertThat(jwtProvider.parseRefreshToken(response.refreshToken())).isEqualTo(1L);
        assertThat(response.user().userId()).isEqualTo(1L);
        assertThat(response.user().nickname()).isEqualTo("하린");
        assertThat(response.user().weeklyBudget()).isNull();
    }

    @Test
    void 이메일은_공백을_제거하고_소문자로_저장한다() {
        when(userRepository.existsByEmail("harin@example.com")).thenReturn(false);
        givenSavedUserGetsId(1L);

        authService.signup(new SignupRequest("하린", "  Harin@Example.COM ", "123456"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("harin@example.com");
    }

    @Test
    void 비밀번호는_BCrypt로_해시해서_저장한다() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        givenSavedUserGetsId(1L);

        authService.signup(new SignupRequest("하린", "harin@example.com", "123456"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isNotEqualTo("123456");
        assertThat(passwordEncoder.matches("123456", saved.getPasswordHash())).isTrue();
        assertThat(saved.getTermsAgreedAt()).isEqualTo(NOW);
    }

    @Test
    void Refresh_Token은_원문이_아니라_해시로_저장한다() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        givenSavedUserGetsId(1L);

        AuthResponse response = authService.signup(new SignupRequest("하린", "harin@example.com", "123456"));

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getTokenHash()).isEqualTo(TokenHasher.sha256(response.refreshToken()));
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
    }

    @Test
    void 이미_가입된_이메일이면_EMAIL_ALREADY_EXISTS() {
        when(userRepository.existsByEmail("harin@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest("하린", "harin@example.com", "123456")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시_가입으로_unique_제약에_걸려도_EMAIL_ALREADY_EXISTS() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uk_users_email"));

        assertThatThrownBy(() -> authService.signup(new SignupRequest("하린", "harin@example.com", "123456")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(refreshTokenRepository, never()).save(any());
    }
}