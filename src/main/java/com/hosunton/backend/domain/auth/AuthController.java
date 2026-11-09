package com.hosunton.backend.domain.auth;

import com.hosunton.backend.domain.auth.dto.AuthResponse;
import com.hosunton.backend.domain.auth.dto.SignupRequest;
import com.hosunton.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "가입 성공 시 Access Token과 Refresh Token을 함께 발급합니다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/signup")
    public ApiResponse<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }
}