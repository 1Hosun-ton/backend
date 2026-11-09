package com.hosunton.backend.global.security.jwt;

import java.time.Instant;

public record IssuedToken(String value, Instant expiresAt) {
}