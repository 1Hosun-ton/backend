package com.hosunton.backend.domain.auth;

import com.hosunton.backend.domain.auth.dto.AuthResponse;
import com.hosunton.backend.domain.auth.dto.SignupRequest;
import com.hosunton.backend.domain.auth.dto.UserSummary;
import com.hosunton.backend.global.exception.BusinessException;
import com.hosunton.backend.global.exception.ErrorCode;
import com.hosunton.backend.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 회원가입_성공시_201과_토큰을_반환한다() throws Exception {
        when(authService.signup(any(SignupRequest.class)))
                .thenReturn(new AuthResponse("access", "refresh", new UserSummary(1L, "하린", null)));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "하린", "email": "harin@example.com", "password": "123456"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.data.user.userId").value(1))
                .andExpect(jsonPath("$.data.user.weeklyBudget").doesNotExist());
    }

    @Test
    void 입력값이_잘못되면_400과_fieldErrors를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "", "email": "not-email", "password": "123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.length()").value(3));
    }

    @Test
    void 이메일이_중복되면_409를_반환한다() throws Exception {
        when(authService.signup(any(SignupRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "하린", "email": "harin@example.com", "password": "123456"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.error.message").value("이미 가입된 이메일이에요"));
    }
}