package com.hosunton.backend.domain.home;

import com.hosunton.backend.domain.home.dto.HomeResponse;
import com.hosunton.backend.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HomeControllerTest {

    private static final Long USER_ID = 1L;

    @Mock
    private HomeService homeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(new HomeController(homeService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 홈_조회_응답_형식() throws Exception {
        when(homeService.getHome(USER_ID)).thenReturn(new HomeResponse(
                "하린",
                new HomeResponse.Week("11월 3주차", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22)),
                HomeResponse.Budget.of(100000, 38000),
                new HomeResponse.Summary(5, 2),
                new HomeResponse.Mission(12L, "주문 전 집에 있는 식재료 확인하기", (short) 2, false)
        ));

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("하린"))
                .andExpect(jsonPath("$.data.week.label").value("11월 3주차"))
                .andExpect(jsonPath("$.data.week.startDate").value("2026-11-16"))
                .andExpect(jsonPath("$.data.budget.remainingBudget").value(62000))
                .andExpect(jsonPath("$.data.budget.overBudget").value(false))
                .andExpect(jsonPath("$.data.summary.regretCount").value(2))
                .andExpect(jsonPath("$.data.mission.missionId").value(12))
                .andExpect(jsonPath("$.data.mission.checkedToday").value(false));
    }

    @Test
    void 미션이_없으면_mission은_null() throws Exception {
        when(homeService.getHome(USER_ID)).thenReturn(new HomeResponse(
                "하린",
                new HomeResponse.Week("11월 3주차", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22)),
                HomeResponse.Budget.of(null, 0),
                new HomeResponse.Summary(0, 0),
                null
        ));

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mission").value(nullValue()))
                .andExpect(jsonPath("$.data.budget.weeklyBudget").value(nullValue()))
                .andExpect(jsonPath("$.data.budget.remainingBudget").value(nullValue()));
    }
}