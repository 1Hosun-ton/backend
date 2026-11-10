package com.hosunton.backend.domain.auth;

import com.hosunton.backend.domain.auth.dto.AuthResponse;
import com.hosunton.backend.domain.auth.dto.LoginRequest;
import com.hosunton.backend.domain.auth.dto.SignupRequest;
import com.hosunton.backend.domain.auth.dto.UserSummary;
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

    private AuthResponse issueTokens(User user) {
        IssuedToken accessToken = jwtProvider.createAccessToken(user.getId());
        IssuedToken refreshToken = jwtProvider.createRefreshToken(user.getId());

        Instant now = clock.instant();
        refreshTokenRepository.save(RefreshToken.create(
                user.getId(),
                TokenHasher.sha256(refreshToken.value()),
                refreshToken.expiresAt(),
                now
        ));

        return new AuthResponse(accessToken.value(), refreshToken.value(), UserSummary.from(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
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

    private boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }

}