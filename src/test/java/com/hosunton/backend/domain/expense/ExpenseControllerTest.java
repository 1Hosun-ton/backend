package com.hosunton.backend.domain.expense;

import com.hosunton.backend.domain.expense.dto.ExpenseCreateRequest;
import com.hosunton.backend.domain.expense.dto.ExpenseListResponse;
import com.hosunton.backend.domain.expense.dto.ExpenseResponse;
import com.hosunton.backend.global.exception.BusinessException;
import com.hosunton.backend.global.exception.ErrorCode;
import com.hosunton.backend.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ExpenseControllerTest {

    private static final Long USER_ID = 1L;

    @Mock
    private ExpenseService expenseService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // JWT 필터(#9)가 SecurityContext에 userId(Long)를 넣는 것을 흉내 낸다.
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(new ExpenseController(expenseService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static ExpenseResponse sampleResponse() {
        return new ExpenseResponse(1L, 25000L, ExpenseItem.DELIVERY, LocalDate.of(2026, 11, 7),
                ExpenseReason.STRESS, true, ExpenseMood.TIRED, "야근 후 배달",
                OffsetDateTime.parse("2026-11-07T13:00:00+09:00"));
    }

    // ===== 등록 =====

    @Test
    void 등록_성공시_201과_기록을_반환한다() throws Exception {
        when(expenseService.create(eq(USER_ID), any(ExpenseCreateRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 25000, "item": "DELIVERY", "spentAt": "2026-11-07",
                                 "reason": "STRESS", "regret": true, "mood": "TIRED", "memo": "야근 후 배달"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.expenseId").value(1))
                .andExpect(jsonPath("$.data.amount").value(25000))
                .andExpect(jsonPath("$.data.item").value("DELIVERY"))
                .andExpect(jsonPath("$.data.spentAt").value("2026-11-07"))
                .andExpect(jsonPath("$.data.reason").value("STRESS"))
                .andExpect(jsonPath("$.data.regret").value(true))
                .andExpect(jsonPath("$.data.mood").value("TIRED"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-07T13:00:00+09:00"));

        ArgumentCaptor<ExpenseCreateRequest> captor = ArgumentCaptor.forClass(ExpenseCreateRequest.class);
        verify(expenseService).create(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().spentAt()).isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @Test
    void 메모가_100자를_넘으면_400() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 1000, "item": "FOOD", "reason": "NEED", "regret": false, "memo": "%s"}
                                """.formatted("가".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("memo"));
        verifyNoInteractions(expenseService);
    }

    @Test
    void 메모_100자는_통과한다() throws Exception {
        when(expenseService.create(eq(USER_ID), any(ExpenseCreateRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 1000, "item": "FOOD", "reason": "NEED", "regret": false, "memo": "%s"}
                                """.formatted("가".repeat(100))))
                .andExpect(status().isCreated());
    }

    @Test
    void 금액이_integer_범위를_넘으면_400() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 2147483648, "item": "FOOD", "reason": "NEED", "regret": false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("amount"));
        verifyNoInteractions(expenseService);
    }

    @Test
    void 항목이_없으면_400() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 1000, "reason": "NEED", "regret": false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("item"));
    }

    @Test
    void 허용되지_않은_Enum_값이면_400() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 1000, "item": "PIZZA", "reason": "NEED", "regret": false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(expenseService);
    }

    @Test
    void 서비스의_전용_에러코드를_그대로_응답한다() throws Exception {
        when(expenseService.create(eq(USER_ID), any(ExpenseCreateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.AMOUNT_REQUIRED));

        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"item": "FOOD", "reason": "NEED", "regret": false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AMOUNT_REQUIRED"))
                .andExpect(jsonPath("$.error.message").value("금액을 입력해 주세요"));
    }

    // ===== 목록 =====

    @Test
    void 월_조회_파라미터를_서비스에_전달한다() throws Exception {
        ExpenseListResponse list = new ExpenseListResponse(
                new ExpenseListResponse.Period(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
                new ExpenseListResponse.Summary(25000L, 2778L, 19446L),
                1,
                List.of(sampleResponse()));
        when(expenseService.list(USER_ID, YearMonth.of(2026, 11), null, null, ExpenseItem.DELIVERY, null))
                .thenReturn(list);

        mockMvc.perform(get("/api/v1/expenses").param("month", "2026-11").param("item", "DELIVERY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period.from").value("2026-11-01"))
                .andExpect(jsonPath("$.data.period.to").value("2026-11-30"))
                .andExpect(jsonPath("$.data.summary.totalAmount").value(25000))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.expenses[0].expenseId").value(1));
    }

    @Test
    void 기간_조회는_summary가_null로_내려간다() throws Exception {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 7);
        when(expenseService.list(USER_ID, null, from, to, null, ExpenseReason.STRESS))
                .thenReturn(new ExpenseListResponse(new ExpenseListResponse.Period(from, to), null, 0, List.of()));

        mockMvc.perform(get("/api/v1/expenses")
                        .param("from", "2026-11-01").param("to", "2026-11-07").param("reason", "STRESS"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"summary\":null")))
                .andExpect(jsonPath("$.data.expenses").isEmpty());
    }

    @Test
    void 잘못된_월_형식이면_400() throws Exception {
        mockMvc.perform(get("/api/v1/expenses").param("month", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(expenseService);
    }

    @Test
    void 잘못된_필터_Enum이면_400() throws Exception {
        mockMvc.perform(get("/api/v1/expenses").param("item", "PIZZA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ===== 삭제 =====

    @Test
    void 삭제_성공시_204() throws Exception {
        mockMvc.perform(delete("/api/v1/expenses/5"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(expenseService).delete(USER_ID, 5L);
    }

    @Test
    void 없거나_다른_사용자의_기록이면_404() throws Exception {
        doThrow(new BusinessException(ErrorCode.NOT_FOUND)).when(expenseService).delete(USER_ID, 5L);

        mockMvc.perform(delete("/api/v1/expenses/5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void ID_형식이_잘못되면_400() throws Exception {
        mockMvc.perform(delete("/api/v1/expenses/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
