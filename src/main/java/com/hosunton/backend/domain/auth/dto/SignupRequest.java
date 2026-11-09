package com.hosunton.backend.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

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
        /**
         * BCrypt는 72바이트까지만 처리한다. @Size는 글자 수만 세므로 한글처럼 1자가 여러 바이트인
         * 문자는 64자 안이어도 72바이트를 넘을 수 있다(한글 25자 = 75바이트).
         */
        @JsonIgnore
        @AssertTrue(message = "비밀번호가 너무 길어요")
        public boolean isPasswordWithinBcryptLimit() {
                return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
}