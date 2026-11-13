package com.hosunton.backend.domain.auth.dto;

public record TokenResponse(String accessToken, String refreshToken) {
}