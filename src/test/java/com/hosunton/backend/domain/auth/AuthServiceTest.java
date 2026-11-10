package com.hosunton.backend.domain.auth;

import com.hosunton.backend.domain.auth.dto.*;
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
import java.util.Optional;

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

    private User savedUser(String email, String rawPassword) {
        User user = User.create(email, "하린", passwordEncoder.encode(rawPassword), NOW);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    @Test
    void 로그인하면_토큰과_사용자_정보를_반환한다() {
        when(userRepository.findByEmail("harin@example.com"))
                .thenReturn(Optional.of(savedUser("harin@example.com", "123456")));

        AuthResponse response = authService.login(new LoginRequest("Harin@Example.com ", "123456"));

        assertThat(jwtProvider.parseAccessToken(response.accessToken())).isEqualTo(1L);
        assertThat(response.user().userId()).isEqualTo(1L);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void 가입되지_않은_이메일이면_INVALID_CREDENTIALS() {
        when(userRepository.findByEmail("none@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("none@example.com", "123456")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void 비밀번호가_틀리면_INVALID_CREDENTIALS() {
        when(userRepository.findByEmail("harin@example.com"))
                .thenReturn(Optional.of(savedUser("harin@example.com", "123456")));

        assertThatThrownBy(() -> authService.login(new LoginRequest("harin@example.com", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void 비밀번호가_72바이트를_넘으면_예외_없이_INVALID_CREDENTIALS() {
        when(userRepository.findByEmail("harin@example.com"))
                .thenReturn(Optional.of(savedUser("harin@example.com", "123456")));

        assertThatThrownBy(() -> authService.login(new LoginRequest("harin@example.com", "가".repeat(25))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void 재발급하면_새_토큰_쌍을_발급하고_기존_토큰은_삭제한다() {
        String oldRefresh = jwtProvider.createRefreshToken(1L).value();
        when(refreshTokenRepository.deleteByTokenHash(TokenHasher.sha256(oldRefresh))).thenReturn(1);

        TokenResponse response = authService.refresh(new RefreshTokenRequest(oldRefresh));

        assertThat(jwtProvider.parseAccessToken(response.accessToken())).isEqualTo(1L);
        assertThat(jwtProvider.parseRefreshToken(response.refreshToken())).isEqualTo(1L);
        assertThat(response.refreshToken()).isNotEqualTo(oldRefresh);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo(TokenHasher.sha256(response.refreshToken()));
    }

    @Test
    void DB에_없는_Refresh_Token이면_INVALID_REFRESH_TOKEN() {
        String refresh = jwtProvider.createRefreshToken(1L).value();
        when(refreshTokenRepository.deleteByTokenHash(any())).thenReturn(0);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(refresh)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void Access_Token으로_재발급하면_DB를_조회하지_않고_INVALID_REFRESH_TOKEN() {
        String access = jwtProvider.createAccessToken(1L).value();

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(access)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        verify(refreshTokenRepository, never()).deleteByTokenHash(any());
    }

    @Test
    void 위조된_Refresh_Token이면_INVALID_REFRESH_TOKEN() {
        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("not-a-jwt")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        verify(refreshTokenRepository, never()).deleteByTokenHash(any());
    }

    @Test
    void 로그아웃하면_해시로_Refresh_Token을_삭제한다() {
        String refresh = jwtProvider.createRefreshToken(1L).value();

        authService.logout(new RefreshTokenRequest(refresh));

        verify(refreshTokenRepository).deleteByTokenHash(TokenHasher.sha256(refresh));
    }

    @Test
    void 이미_없는_토큰으로_로그아웃해도_예외가_나지_않는다() {
        when(refreshTokenRepository.deleteByTokenHash(any())).thenReturn(0);

        authService.logout(new RefreshTokenRequest("already-deleted-or-garbage"));

        verify(refreshTokenRepository).deleteByTokenHash(TokenHasher.sha256("already-deleted-or-garbage"));
    }
}