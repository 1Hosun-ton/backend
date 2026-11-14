package com.hosunton.backend.domain.insight;

import com.hosunton.backend.domain.expense.ExpenseChangedEvent;
import com.hosunton.backend.domain.insight.ai.InsightWriter;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 소비 기록이 바뀌어 커밋되면, 그 날짜가 속한 주간·월간 인사이트를 백그라운드에서 미리 만들어 저장한다.
 * 사용자가 인사이트 탭을 열 때 AI 응답을 기다리지 않고 저장된 결과를 바로 받게 하려는 것이다.
 *
 * <p>작업은 스레드 하나에서 순서대로 처리한다. 앞 작업이 AI 응답을 기다리는 동안 새 기록이 커밋되면
 * 앞 작업이 옛 데이터로 저장할 수 있으므로, 각 작업은 해당 기간의 저장 결과를 먼저 지우고 다시 만든다.
 */
@Slf4j
@Component
public class InsightPrefetcher {

    private static final int QUEUE_CAPACITY = 200;

    private final InsightService insightService;
    private final InsightCacheService insightCacheService;
    private final InsightWriter insightWriter;
    private final ExecutorService executor;
    /** 아직 시작하지 않은 작업 (userId:날짜). 같은 요청이 여러 번 쌓이지 않게 한다. */
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    @Autowired
    public InsightPrefetcher(InsightService insightService, InsightCacheService insightCacheService,
                             InsightWriter insightWriter) {
        this(insightService, insightCacheService, insightWriter, new ThreadPoolExecutor(1, 1, 0L,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
                    Thread thread = new Thread(runnable, "insight-prefetch");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    InsightPrefetcher(InsightService insightService, InsightCacheService insightCacheService,
                      InsightWriter insightWriter, ExecutorService executor) {
        this.insightService = insightService;
        this.insightCacheService = insightCacheService;
        this.insightWriter = insightWriter;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExpenseChanged(ExpenseChangedEvent event) {
        if (!insightWriter.isAvailable()) {
            return; // AI 없이 만든 대체 문장은 저장하지 않으므로 미리 만들 이유가 없다.
        }
        String key = event.userId() + ":" + event.spentOn();
        if (!pending.add(key)) {
            return; // 같은 날짜 작업이 이미 대기 중이면 그 작업이 최신 데이터로 처리한다.
        }
        try {
            executor.execute(() -> prefetch(key, event.userId(), event.spentOn()));
        } catch (RejectedExecutionException e) {
            pending.remove(key);
            log.warn("Insight prefetch queue is full, skipped (userId={}, date={})", event.userId(), event.spentOn());
        }
    }

    private void prefetch(String key, Long userId, LocalDate spentOn) {
        pending.remove(key); // 시작 후 들어온 변경은 새 작업으로 다시 처리한다.
        long startedAt = System.currentTimeMillis();
        try {
            insightCacheService.invalidate(userId, spentOn);
            // 인사이트 화면의 기본값이 월간이라 월간을 먼저 만든다.
            for (PeriodType type : List.of(PeriodType.MONTH, PeriodType.WEEK)) {
                insightService.getInsight(userId, type, type.startOf(spentOn));
            }
            log.info("Insight prefetched (userId={}, date={}, {}ms)", userId, spentOn,
                    System.currentTimeMillis() - startedAt);
        } catch (RuntimeException e) {
            // 미리 만들기는 최적화일 뿐이다. 실패하면 사용자가 열 때 기존처럼 만든다.
            log.warn("Insight prefetch failed (userId={}, date={}): {}", userId, spentOn, e.toString());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
