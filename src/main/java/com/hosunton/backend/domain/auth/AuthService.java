package com.hosunton.backend.domain.auth;

import com.hosunton.backend.domain.auth.dto.*;
import com.hosunton.backend.domain.user.User;
import com.hosunton.backend.domain.user.UserRepository;
import com.hosunton.backend.global.exception.BusinessException;
import com.hosunton.backend.global.exception.ErrorCode;
import com.hosunton.backend.global.security.jwt.IssuedToken;
import com.hosunton.backend.global.security.jwt.JwtProvider;
import com.hosunton.backend.global.security.jwt.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final Clock clock;
    private static final int BCRYPT_MAX_BYTES = 72;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.create(
                email,
                request.nickname().trim(),
                passwordEncoder.encode(request.password()),
                clock.instant()
        );

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (exceedsBcryptLimit(request.password())
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        String token = request.refreshToken();

        Long userId = jwtProvider.parseRefreshToken(token);

        int deleted = refreshTokenRepository.deleteByTokenHash(TokenHasher.sha256(token));
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        return issueTokenPair(userId);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenRepository.deleteByTokenHash(TokenHasher.sha256(request.refreshToken()));
    }

    private AuthResponse issueTokens(User user) {
        TokenResponse tokens = issueTokenPair(user.getId());
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), UserSummary.from(user));
    }

    private TokenResponse issueTokenPair(Long userId) {
        IssuedToken accessToken = jwtProvider.createAccessToken(userId);
        IssuedToken refreshToken = jwtProvider.createRefreshToken(userId);

        refreshTokenRepository.save(RefreshToken.create(
                userId,
                TokenHasher.sha256(refreshToken.value()),
                refreshToken.expiresAt(),
                clock.instant()
        ));

        return new TokenResponse(accessToken.value(), refreshToken.value());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
    private boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }

}