package com.hosunton.backend.domain.user;

import com.hosunton.backend.domain.user.dto.MeResponse;
import com.hosunton.backend.domain.user.dto.WeeklyBudgetRequest;
import com.hosunton.backend.domain.user.dto.WeeklyBudgetResponse;
import com.hosunton.backend.global.exception.BusinessException;
import com.hosunton.backend.global.exception.ErrorCode;
import com.hosunton.backend.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // JWT 필터가 SecurityContext에 userId(Long)를 넣는 것을 흉내 낸다.
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 내_정보_조회() throws Exception {
        when(userService.getMe(USER_ID))
                .thenReturn(new MeResponse(USER_ID, "하린", "harin@example.com", 100000, true, 12L));

        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.nickname").value("하린"))
                .andExpect(jsonPath("$.data.weeklyBudget").value(100000))
                .andExpect(jsonPath("$.data.budgetSet").value(true))
                .andExpect(jsonPath("$.data.totalExpenseCount").value(12));
    }

    @Test
    void 주간_예산_설정() throws Exception {
        when(userService.updateWeeklyBudget(eq(USER_ID), any(WeeklyBudgetRequest.class)))
                .thenReturn(new WeeklyBudgetResponse(100000));

        mockMvc.perform(put("/api/v1/me/weekly-budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weeklyBudget": 100000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeklyBudget").value(100000));
    }

    @Test
    void 잘못된_예산이면_400_INVALID_BUDGET() throws Exception {
        when(userService.updateWeeklyBudget(eq(USER_ID), any(WeeklyBudgetRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.INVALID_BUDGET));

        mockMvc.perform(put("/api/v1/me/weekly-budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weeklyBudget": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_BUDGET"))
                .andExpect(jsonPath("$.error.message").value("1원 이상 입력해 주세요"));
    }

    @Test
    void 숫자가_아닌_예산이면_400_VALIDATION_ERROR() throws Exception {
        mockMvc.perform(put("/api/v1/me/weekly-budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weeklyBudget": "십만원"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 회원_탈퇴_204_바디_없음() throws Exception {
        mockMvc.perform(delete("/api/v1/me"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(userService).withdraw(USER_ID);
    }
}