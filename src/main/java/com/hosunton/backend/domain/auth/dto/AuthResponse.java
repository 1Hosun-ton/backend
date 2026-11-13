package com.hosunton.backend.domain.auth.dto;

public record AuthResponse(String accessToken, String refreshToken, UserSummary user) {
}