package com.hosunton.backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken이 필요해요")
        String refreshToken
) {
}