package com.hosunton.backend.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "닉네임을 입력해 주세요")
        @Size(max = 20, message = "닉네임은 20자까지 입력할 수 있어요")
        String nickname,

        @NotBlank(message = "이메일을 입력해 주세요")
        @Email(message = "이메일 형식이 아니에요")
        @Size(max = 255, message = "이메일이 너무 길어요")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요")
        @Size(min = 6, max = 64, message = "비밀번호는 6자 이상 64자 이하로 입력해 주세요")
        String password
) {
}