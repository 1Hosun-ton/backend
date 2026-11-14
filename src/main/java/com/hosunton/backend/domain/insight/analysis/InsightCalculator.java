package com.hosunton.backend.domain.insight.analysis;

import com.hosunton.backend.domain.expense.Expense;
import com.hosunton.backend.domain.expense.ExpenseItem;
import com.hosunton.backend.domain.expense.ExpenseMood;
import com.hosunton.backend.domain.expense.ExpenseReason;
import com.hosunton.backend.domain.insight.PeriodType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
        InsightAnalysis.Pattern pattern = findKeyPattern(current);
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
                pattern,
                pattern == null ? List.of() : findings(pattern, current)
        );
    }

    // ===== 발견 (계기 → 결과) =====

    /**
     * 핵심 패턴 기록에서 사용자가 돌아보고 바꿔 볼 만한 발견을 찾아 강한 순으로 돌려준다. 마지막은 항상 BASIC 이다.
     * 패턴 기록은 ITEM_AMOUNT 면 이유×항목, 그 외는 그 이유의 기록이고, 나머지는 이번 기간의 다른 기록 전부다.
     */
    public static List<InsightAnalysis.Finding> findings(InsightAnalysis.Pattern p, List<Expense> current) {
        boolean byItem = p.type() == InsightAnalysis.PatternType.ITEM_AMOUNT;
        List<Expense> mine = new ArrayList<>();
        List<Expense> others = new ArrayList<>();
        for (Expense e : current) {
            boolean inPattern = e.getReason() == p.reason() && (!byItem || e.getItem() == p.item());
            (inPattern ? mine : others).add(e);
        }
        int count = mine.size();
        long mineRegret = regretCount(mine);
        long othersRegret = regretCount(others);
        long totalRegret = mineRegret + othersRegret;
        List<InsightAnalysis.Finding> found = new ArrayList<>();

        // 의도와 결과의 어긋남: 아끼려던 할인이 더 비쌈 / 기분을 풀려던·필요했던·즉흥 소비가 후회로 남음
        if (p.reason() == ExpenseReason.DISCOUNT && p.othersAverage() > 0
                && p.comboAverage() * 10 >= p.othersAverage() * 13) {
            found.add(finding(InsightAnalysis.FindingType.INTENT_BACKFIRE, 88,
                    "count", count, "average", p.comboAverage(), "othersAverage", p.othersAverage(),
                    "averageRatio", ratio(p.comboAverage(), p.othersAverage()),
                    // ITEM_AMOUNT 는 같은 항목 안에서만 비교했으므로 이유 전체 기준의 "가장 비싼"을 허용하지 않는다.
                    "highestAverageAmongReasons", !byItem && isHighestAverage(p.reason(), current)));
        } else if (p.reason() != ExpenseReason.DISCOUNT && p.reason() != ExpenseReason.HABIT
                && mineRegret >= 2 && mineRegret * 2 >= count) {
            found.add(finding(InsightAnalysis.FindingType.INTENT_BACKFIRE, p.reason() == ExpenseReason.NEED ? 70 : 86,
                    "count", count, "regretCount", (int) mineRegret,
                    "othersCount", others.size(), "othersRegretCount", (int) othersRegret));
        }

        // 이번 기간 후회가 이 패턴에 몰림
        if (mineRegret >= 2 && !others.isEmpty() && mineRegret * 10 >= totalRegret * 6) {
            found.add(finding(InsightAnalysis.FindingType.REGRET_CONCENTRATION, mineRegret == totalRegret ? 90 : 84,
                    "regretCount", (int) mineRegret, "totalRegretCount", (int) totalRegret));
        }

        // 기분이 계기 (보통은 대비가 없어 뺀다)
        InsightAnalysis.Finding mood = null;
        long bestMood = 0;
        for (ExpenseMood m : List.of(ExpenseMood.TIRED, ExpenseMood.DEPRESSED, ExpenseMood.GOOD)) {
            long mineMood = mine.stream().filter(e -> e.getMood() == m).count();
            long totalMood = current.stream().filter(e -> e.getMood() == m).count();
            if (mineMood >= 2 && mineMood * 2 >= count && mineMood * 10 >= totalMood * 6 && mineMood > bestMood) {
                bestMood = mineMood;
                mood = finding(InsightAnalysis.FindingType.MOOD_TRIGGER, m == ExpenseMood.GOOD ? 60 : 82,
                        "mood", m, "moodCount", (int) mineMood, "totalMoodCount", (int) totalMood, "count", count);
            }
        }
        if (mood != null) {
            found.add(mood);
        }

        // 요일·주말이 계기
        InsightAnalysis.Finding day = dayTrigger(mine, others);
        if (day != null) {
            found.add(day);
        }

        // 한 번은 작아도 모이면 큼 (같으면 "넘었다"고 할 수 없어 제외)
        long mineTotal = sum(mine);
        Expense biggestOther = others.stream().max(Comparator.comparingInt(Expense::getAmount)).orElse(null);
        if (count >= 3 && p.comboAverage() <= 10_000 && biggestOther != null && mineTotal > biggestOther.getAmount()) {
            found.add(finding(InsightAnalysis.FindingType.SMALL_ADDS_UP, p.reason() == ExpenseReason.HABIT ? 76 : 58,
                    "count", count, "total", mineTotal, "average", p.comboAverage(),
                    "biggestOtherItem", biggestOther.getItem(), "biggestOtherAmount", biggestOther.getAmount()));
        }

        // 기록 수 비중보다 금액 비중이 큼
        long total = sum(current);
        int countShare = (int) Math.round(count * 100.0 / current.size());
        int amountShare = total == 0 ? 0 : (int) Math.round(mineTotal * 100.0 / total);
        if (amountShare - countShare >= 20) {
            found.add(finding(InsightAnalysis.FindingType.AMOUNT_WEIGHT, 62,
                    "count", count, "expenseCount", current.size(), "countShare", countShare, "amountShare", amountShare));
        }

        // 후회율이 나머지보다 30%p 이상 높음 (정수 비교)
        if (mineRegret >= 2 && !others.isEmpty()
                && mineRegret * 100 * others.size() - othersRegret * 100 * count >= 30L * count * others.size()) {
            found.add(finding(InsightAnalysis.FindingType.REGRET_GAP, 70,
                    "count", count, "regretCount", (int) mineRegret,
                    "othersCount", others.size(), "othersRegretCount", (int) othersRegret));
        }

        // 보조: 후회가 하나도 없는 다른 이유 (2건 이상)
        if (mineRegret >= 1) {
            Map<ExpenseReason, List<Expense>> byReason = new EnumMap<>(ExpenseReason.class);
            others.forEach(e -> byReason.computeIfAbsent(e.getReason(), r -> new ArrayList<>()).add(e));
            byReason.entrySet().stream()
                    .filter(en -> en.getKey() != p.reason() && en.getValue().size() >= 2 && regretCount(en.getValue()) == 0)
                    .max(Comparator.comparingInt((Map.Entry<ExpenseReason, List<Expense>> en) -> en.getValue().size())
                            .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                    .ifPresent(en -> found.add(finding(InsightAnalysis.FindingType.SAFE_ZONE, 40,
                            "reason", en.getKey(), "count", en.getValue().size())));
        }

        found.sort(Comparator.comparingInt(InsightAnalysis.Finding::strength).reversed());
        found.add(finding(InsightAnalysis.FindingType.BASIC, 10,
                "count", count, "total", mineTotal, "average", p.comboAverage(), "othersAverage", p.othersAverage(),
                "regretCount", (int) mineRegret, "shareOfTotal", amountShare,
                "representativeItemCount", (int) mine.stream().filter(e -> e.getItem() == p.item()).count()));
        return found;
    }

    /** 패턴 기록이 모두 같은 요일(3건 이상)이거나, 주말에 몰리고 나머지는 주말이 적을 때. */
    private static InsightAnalysis.Finding dayTrigger(List<Expense> mine, List<Expense> others) {
        int count = mine.size();
        if (count >= 3) {
            DayOfWeek first = mine.getFirst().getSpentOn().getDayOfWeek();
            if (mine.stream().allMatch(e -> e.getSpentOn().getDayOfWeek() == first)) {
                return finding(InsightAnalysis.FindingType.DAY_TRIGGER, 68, "day", first, "dayCount", count, "count", count);
            }
        }
        long mineWeekend = mine.stream().filter(InsightCalculator::isWeekend).count();
        long othersWeekend = others.stream().filter(InsightCalculator::isWeekend).count();
        if (count >= 2 && mineWeekend * 4 >= count * 3L && others.size() >= 2 && othersWeekend * 10 <= others.size() * 4L) {
            return finding(InsightAnalysis.FindingType.DAY_TRIGGER, 64, "day", "주말", "dayCount", (int) mineWeekend,
                    "count", count);
        }
        return null;
    }

    private static boolean isWeekend(Expense e) {
        DayOfWeek d = e.getSpentOn().getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }

    /** 이 이유의 건당 평균이 이번 기간 이유들 중 가장 높은지 */
    private static boolean isHighestAverage(ExpenseReason reason, List<Expense> current) {
        Map<ExpenseReason, long[]> byReason = new EnumMap<>(ExpenseReason.class); // [sum, count]
        for (Expense e : current) {
            long[] acc = byReason.computeIfAbsent(e.getReason(), r -> new long[2]);
            acc[0] += e.getAmount();
            acc[1]++;
        }
        long[] mine = byReason.get(reason);
        return byReason.entrySet().stream().filter(en -> en.getKey() != reason)
                .allMatch(en -> mine[0] * en.getValue()[1] > en.getValue()[0] * mine[1]);
    }

    /** a ÷ b, 소수 첫째 자리 */
    private static double ratio(long a, long b) {
        return BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b), 1, RoundingMode.HALF_UP).doubleValue();
    }

    private static InsightAnalysis.Finding finding(InsightAnalysis.FindingType type, int strength, Object... keyValues) {
        Map<String, Object> facts = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            facts.put((String) keyValues[i], keyValues[i + 1]);
        }
        return new InsightAnalysis.Finding(type, strength, facts);
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
