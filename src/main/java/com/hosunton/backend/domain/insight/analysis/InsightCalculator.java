package com.hosunton.backend.domain.insight.analysis;

import com.hosunton.backend.domain.expense.Expense;
import com.hosunton.backend.domain.expense.ExpenseItem;
import com.hosunton.backend.domain.expense.ExpenseReason;
import com.hosunton.backend.domain.insight.PeriodType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 인사이트 통계·패턴 계산 (기획안 7-1). DB·LLM에 의존하지 않는 순수 계산이다.
 */
public final class InsightCalculator {

    /** 이유×항목 조합이 후보가 되려면 필요한 최소 건수 */
    static final int MIN_PATTERN_COUNT = 2;
    /** 비교 평균 대비 몇 % 이상 높아야 후보인지 */
    static final int MIN_EXCESS_PERCENT = 20;

    private InsightCalculator() {
    }

    /**
     * @param current  조회 기간의 기록
     * @param previous 직전 기간의 기록
     */
    public static InsightAnalysis analyze(PeriodType type, LocalDate periodStart,
                                          List<Expense> current, List<Expense> previous) {
        long total = sum(current);
        long previousTotal = sum(previous);
        int regretCount = (int) current.stream().filter(Expense::isRegret).count();

        InsightAnalysis.PreviousSummary previousSummary = previous.isEmpty() ? null
                : new InsightAnalysis.PreviousSummary(
                        previousTotal,
                        (int) previous.stream().filter(Expense::isRegret).count(),
                        changeRate(total, previousTotal));

        return new InsightAnalysis(
                total,
                current.size(),
                regretCount,
                reasonBreakdown(current, total, previous.isEmpty() ? null : previous, previousTotal),
                trend(type, periodStart, current),
                previousSummary,
                findKeyPattern(current)
        );
    }

    // ===== 이유별 비중 =====

    private static List<InsightAnalysis.ReasonShare> reasonBreakdown(List<Expense> current, long total,
                                                                    List<Expense> previous, long previousTotal) {
        Map<ExpenseReason, long[]> byReason = new EnumMap<>(ExpenseReason.class); // [amount, count]
        for (Expense e : current) {
            long[] acc = byReason.computeIfAbsent(e.getReason(), r -> new long[2]);
            acc[0] += e.getAmount();
            acc[1]++;
        }
        Map<ExpenseReason, Long> previousByReason = new EnumMap<>(ExpenseReason.class);
        if (previous != null) {
            for (Expense e : previous) {
                previousByReason.merge(e.getReason(), (long) e.getAmount(), Long::sum);
            }
        }

        List<InsightAnalysis.ReasonShare> shares = new ArrayList<>();
        byReason.forEach((reason, acc) -> {
            double ratio = percent(acc[0], total, 2);
            Double previousRatio = previous == null ? null
                    : percent(previousByReason.getOrDefault(reason, 0L), previousTotal, 2);
            Double change = previousRatio == null ? null
                    : BigDecimal.valueOf(ratio).subtract(BigDecimal.valueOf(previousRatio)).doubleValue();
            shares.add(new InsightAnalysis.ReasonShare(reason, acc[0], (int) acc[1], ratio, previousRatio, change));
        });
        shares.sort(Comparator.comparingLong(InsightAnalysis.ReasonShare::amount).reversed()
                .thenComparing(InsightAnalysis.ReasonShare::reason));
        return shares;
    }

    // ===== 추이 =====

    private static List<InsightAnalysis.TrendPoint> trend(PeriodType type, LocalDate periodStart, List<Expense> current) {
        LocalDate periodEnd = type.endOf(periodStart);
        List<InsightAnalysis.TrendPoint> points = new ArrayList<>();
        if (type == PeriodType.WEEK) {
            for (LocalDate day = periodStart; !day.isAfter(periodEnd); day = day.plusDays(1)) {
                points.add(new InsightAnalysis.TrendPoint(day, sumBetween(current, day, day)));
            }
            return points;
        }
        // 월간: 월요일 시작 주 단위로 나누고, 각 주차는 그 달 안의 첫날을 대표 날짜로 쓴다.
        LocalDate weekStart = PeriodType.WEEK.startOf(periodStart);
        while (!weekStart.isAfter(periodEnd)) {
            LocalDate from = weekStart.isBefore(periodStart) ? periodStart : weekStart;
            LocalDate weekEnd = weekStart.plusDays(6);
            LocalDate to = weekEnd.isAfter(periodEnd) ? periodEnd : weekEnd;
            points.add(new InsightAnalysis.TrendPoint(from, sumBetween(current, from, to)));
            weekStart = weekStart.plusWeeks(1);
        }
        return points;
    }

    // ===== 패턴 후보 =====

    public static InsightAnalysis.Pattern findPattern(List<Expense> current) {
        Map<ExpenseItem, List<Expense>> byItem = new EnumMap<>(ExpenseItem.class);
        for (Expense e : current) {
            byItem.computeIfAbsent(e.getItem(), i -> new ArrayList<>()).add(e);
        }

        InsightAnalysis.Pattern best = null;
        for (Map.Entry<ExpenseItem, List<Expense>> entry : byItem.entrySet()) {
            ExpenseItem item = entry.getKey();
            List<Expense> itemExpenses = entry.getValue();
            long itemTotal = sum(itemExpenses);

            Map<ExpenseReason, long[]> byReason = new EnumMap<>(ExpenseReason.class); // [sum, count, regret]
            for (Expense e : itemExpenses) {
                long[] acc = byReason.computeIfAbsent(e.getReason(), r -> new long[3]);
                acc[0] += e.getAmount();
                acc[1]++;
                if (e.isRegret()) {
                    acc[2]++;
                }
            }

            for (Map.Entry<ExpenseReason, long[]> combo : byReason.entrySet()) {
                long comboSum = combo.getValue()[0];
                long comboCount = combo.getValue()[1];
                long othersSum = itemTotal - comboSum;
                long othersCount = itemExpenses.size() - comboCount;
                if (comboCount < MIN_PATTERN_COUNT || othersCount == 0) {
                    continue; // 2건 미만이거나 비교할 나머지 기록이 없으면 후보 불가
                }
                // comboSum/comboCount >= othersSum/othersCount × 1.2 를 정수로 비교 (경계값 오차 방지)
                boolean exceeds = comboSum * othersCount * 100
                        >= othersSum * comboCount * (100 + MIN_EXCESS_PERCENT);
                if (!exceeds) {
                    continue;
                }
                InsightAnalysis.Pattern candidate = new InsightAnalysis.Pattern(
                        combo.getKey(), item, (int) comboCount, comboSum,
                        Math.round((double) comboSum / comboCount),
                        Math.round((double) othersSum / othersCount),
                        itemTotal, (int) combo.getValue()[2], InsightAnalysis.PatternType.ITEM_AMOUNT);
                if (best == null || isBetter(candidate, best)) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    /**
     * 인사이트·미션이 함께 쓰는 핵심 패턴. 아래 순서로 찾고 처음 맞는 것을 쓴다. 모두 아니면 null.
     * ① 같은 항목 안 이유×항목 (findPattern) ② 이유 건당 금액 20%↑ ③ 이유 절반 이상 후회 ④ 가장 잦은 이유.
     * ②~④는 같은 이유 기록이 2건 이상이어야 한다.
     */
    public static InsightAnalysis.Pattern findKeyPattern(List<Expense> current) {
        InsightAnalysis.Pattern itemPattern = findPattern(current);
        if (itemPattern != null) {
            return itemPattern;
        }

        Map<ExpenseReason, List<Expense>> byReason = new EnumMap<>(ExpenseReason.class);
        for (Expense e : current) {
            byReason.computeIfAbsent(e.getReason(), r -> new ArrayList<>()).add(e);
        }
        long total = sum(current);
        List<ExpenseReason> candidates = byReason.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= MIN_PATTERN_COUNT)
                .map(Map.Entry::getKey)
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }

        // ② 다른 이유 전체 평균보다 건당 20% 이상 높음 (합계 큰 순). 정수로 비교해 경계값 오차를 막는다.
        ExpenseReason amountReason = candidates.stream()
                .filter(r -> {
                    long reasonSum = sum(byReason.get(r));
                    long reasonCount = byReason.get(r).size();
                    long othersCount = current.size() - reasonCount;
                    return othersCount > 0 && reasonSum * othersCount * 100
                            >= (total - reasonSum) * reasonCount * (100 + MIN_EXCESS_PERCENT);
                })
                .max(Comparator.comparingLong((ExpenseReason r) -> sum(byReason.get(r)))
                        .thenComparingInt(r -> byReason.get(r).size())
                        .thenComparing(Comparator.reverseOrder()))
                .orElse(null);
        if (amountReason != null) {
            return reasonPattern(amountReason, byReason.get(amountReason), current,
                    InsightAnalysis.PatternType.REASON_AMOUNT);
        }

        // ③ 절반 이상 후회 (후회 건수 많은 순)
        ExpenseReason regretReason = candidates.stream()
                .filter(r -> regretCount(byReason.get(r)) * 2 >= byReason.get(r).size())
                .max(Comparator.comparingLong((ExpenseReason r) -> regretCount(byReason.get(r)))
                        .thenComparingLong(r -> sum(byReason.get(r)))
                        .thenComparing(Comparator.reverseOrder()))
                .orElse(null);
        if (regretReason != null) {
            return reasonPattern(regretReason, byReason.get(regretReason), current,
                    InsightAnalysis.PatternType.REASON_REGRET);
        }

        // ④ 가장 잦은 이유 (같으면 합계 큰 순)
        ExpenseReason frequentReason = candidates.stream()
                .max(Comparator.comparingInt((ExpenseReason r) -> byReason.get(r).size())
                        .thenComparingLong(r -> sum(byReason.get(r)))
                        .thenComparing(Comparator.reverseOrder()))
                .orElseThrow();
        return reasonPattern(frequentReason, byReason.get(frequentReason), current,
                InsightAnalysis.PatternType.REASON_FREQUENT);
    }

    /** 이유 기준 패턴. 대표 항목은 그 이유로 가장 많이 쓴 항목 (같으면 건수, 그다음 enum 순서). */
    private static InsightAnalysis.Pattern reasonPattern(ExpenseReason reason, List<Expense> reasonExpenses,
                                                         List<Expense> current, InsightAnalysis.PatternType type) {
        Map<ExpenseItem, long[]> byItem = new EnumMap<>(ExpenseItem.class); // [sum, count]
        for (Expense e : reasonExpenses) {
            long[] acc = byItem.computeIfAbsent(e.getItem(), i -> new long[2]);
            acc[0] += e.getAmount();
            acc[1]++;
        }
        ExpenseItem item = byItem.entrySet().stream()
                .max(Comparator.comparingLong((Map.Entry<ExpenseItem, long[]> en) -> en.getValue()[0])
                        .thenComparingLong(en -> en.getValue()[1])
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .orElseThrow()
                .getKey();
        long reasonSum = sum(reasonExpenses);
        int reasonCount = reasonExpenses.size();
        long othersSum = sum(current) - reasonSum;
        int othersCount = current.size() - reasonCount;
        return new InsightAnalysis.Pattern(reason, item, reasonCount, reasonSum,
                Math.round((double) reasonSum / reasonCount),
                othersCount == 0 ? 0 : Math.round((double) othersSum / othersCount),
                byItem.get(item)[0], (int) regretCount(reasonExpenses), type);
    }

    private static long regretCount(List<Expense> expenses) {
        return expenses.stream().filter(Expense::isRegret).count();
    }

    /** 조합 합계가 큰 쪽. 같으면 건수가 많은 쪽, 그래도 같으면 enum 순서로 고정. */
    private static boolean isBetter(InsightAnalysis.Pattern a, InsightAnalysis.Pattern b) {
        if (a.comboTotal() != b.comboTotal()) {
            return a.comboTotal() > b.comboTotal();
        }
        if (a.comboCount() != b.comboCount()) {
            return a.comboCount() > b.comboCount();
        }
        int byItem = a.item().compareTo(b.item());
        return byItem != 0 ? byItem < 0 : a.reason().compareTo(b.reason()) < 0;
    }

    // ===== 유틸 =====

    private static long sum(List<Expense> expenses) {
        return expenses.stream().mapToLong(Expense::getAmount).sum();
    }

    private static long sumBetween(List<Expense> expenses, LocalDate from, LocalDate to) {
        return expenses.stream()
                .filter(e -> !e.getSpentOn().isBefore(from) && !e.getSpentOn().isAfter(to))
                .mapToLong(Expense::getAmount)
                .sum();
    }

    private static double percent(long part, long whole, int scale) {
        if (whole == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(whole), scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** (current - previous) / previous × 100, 소수 첫째 자리 */
    private static double changeRate(long current, long previous) {
        if (previous == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(current - previous).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
