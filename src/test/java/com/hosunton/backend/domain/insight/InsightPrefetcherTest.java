package com.hosunton.backend.domain.insight;

import com.hosunton.backend.domain.expense.ExpenseChangedEvent;
import com.hosunton.backend.domain.insight.ai.InsightWriter;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 기록 변경 후 해당 날짜의 주간·월간 인사이트를 새로 만든다. AI가 없으면 아무것도 하지 않는다. */
class InsightPrefetcherTest {

    private final InsightService insightService = mock(InsightService.class);
    private final InsightCacheService cacheService = mock(InsightCacheService.class);
    private final InsightWriter writer = mock(InsightWriter.class);

    @Test
    void 기록_날짜의_주간_월간을_지우고_다시_만든다() throws Exception {
        when(writer.isAvailable()).thenReturn(true);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        LocalDate date = LocalDate.of(2026, 11, 12); // 목요일

        new InsightPrefetcher(insightService, cacheService, writer, executor)
                .onExpenseChanged(new ExpenseChangedEvent(1L, date));
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        verify(cacheService).invalidate(1L, date);
        verify(insightService).getInsight(1L, PeriodType.WEEK, LocalDate.of(2026, 11, 9));
        verify(insightService).getInsight(1L, PeriodType.MONTH, LocalDate.of(2026, 11, 1));
    }

    @Test
    void AI가_없으면_미리_만들지_않는다() {
        when(writer.isAvailable()).thenReturn(false);
        new InsightPrefetcher(insightService, cacheService, writer, Executors.newSingleThreadExecutor())
                .onExpenseChanged(new ExpenseChangedEvent(1L, LocalDate.of(2026, 11, 12)));
        verify(insightService, never()).getInsight(any(), any(), any());
    }
}
