package com.hosunton.backend.domain.insight.ai;

import com.hosunton.backend.domain.expense.ExpenseItem;
import com.hosunton.backend.domain.expense.ExpenseMood;
import com.hosunton.backend.domain.expense.ExpenseReason;
import com.hosunton.backend.domain.insight.InsightLabels;
import com.hosunton.backend.domain.insight.PeriodType;
import com.hosunton.backend.domain.insight.analysis.InsightAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * 계산된 통계를 문장으로 바꾼다 (기획안 7-1 3~4단계).
 * LLM 호출 → 형식·수치 검증 → 실패 시 1회 재시도 → 그래도 실패하면 규칙 기반 대체 문장.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsightNarrator {

    private static final int MAX_ATTEMPTS = 2;
    private static final int MAX_INSIGHT_LENGTH = 110;
    private static final int MAX_COMPARISON_LENGTH = 60;
    private static final int MAX_SUGGESTION_LENGTH = 60;
    private static final int MAX_QUESTION_LENGTH = 55;
    private static final java.util.regex.Pattern LATIN = java.util.regex.Pattern.compile("[A-Za-z]");
    /** 훈계·단정·격식체 표현 (기획안 7-1 제약). 프롬프트에서도 금지하지만 모델이 어길 때를 대비해 코드로 막는다. */
    private static final List<String> BANNED_EXPRESSIONS = List.of(
            "해야 해요", "해야 합니다", "하세요", "줄이세요", "낭비", "과소비", "문제", "나쁜", "절대", "반드시",
            "습니다", "됩니다", "!", "！");
    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

    private final InsightWriter insightWriter;
    private final JsonMapper jsonMapper;

    public record Narration(InsightText text, boolean fromAi) {
    }

    public Narration narrate(PeriodType type, LocalDate start, LocalDate end, InsightAnalysis analysis) {
        if (insightWriter.isAvailable()) {
            Map<String, Object> stats = statsPayload(type, start, end, analysis);
            String statsJson = jsonMapper.writeValueAsString(stats);
            Set<String> allowedNumbers = allowedNumbers(stats);
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    InsightText text = insightWriter.write(statsJson);
                    return new Narration(validate(text, analysis.previous() != null, allowedNumbers), true);
                } catch (InsightGenerationException e) {
                    log.warn("Insight generation failed (attempt {}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
                }
            }
        }
        return new Narration(fallback(type, analysis), false);
    }

    // ===== LLM 입력: 집계 통계만 (메모 원문·사용자 정보 없음) =====

    Map<String, Object> statsPayload(PeriodType type, LocalDate start, LocalDate end, InsightAnalysis a) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("period", Map.of("type", InsightLabels.of(type),
                "startDate", start.toString(), "endDate", end.toString()));

        Map<String, Object> current = new LinkedHashMap<>();
        current.put("totalAmount", a.totalAmount());
        current.put("expenseCount", a.expenseCount());
        current.put("regretCount", a.regretCount());
        current.put("reasonBreakdown", a.reasonBreakdown().stream()
                .map(s -> Map.of("reason", InsightLabels.of(s.reason()), "amount", s.amount(),
                        "count", s.count(), "ratio", s.ratio()))
                .toList());
        InsightAnalysis.Pattern p = a.pattern();
        Map<String, Object> keyPattern = new LinkedHashMap<>();
        keyPattern.put("patternType", p.type().name());
        keyPattern.put("reason", InsightLabels.of(p.reason()));
        keyPattern.put("item", InsightLabels.of(p.item()));
        keyPattern.put("count", p.comboCount());
        keyPattern.put("total", p.comboTotal());
        keyPattern.put("regretCount", p.comboRegretCount());
        keyPattern.put("representativeItemCount", representativeItemCount(a));
        current.put("keyPattern", keyPattern);
        payload.put("findings", findingsPayload(a));
        payload.put("current_stats", current);

        if (a.previous() == null) {
            payload.put("previous_stats", null);
            payload.put("changes", null);
        } else {
            payload.put("previous_stats", Map.of("totalAmount", a.previous().totalAmount(),
                    "regretCount", a.previous().regretCount()));
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("totalAmountChangeRate", a.previous().totalAmountChangeRate());
            changes.put("regretCountChange", a.regretCount() - a.previous().regretCount());
            // 핵심 패턴 이유의 비중 변화(%p). 직전 기간에 그 이유가 없었으면 비중 0에서의 변화다.
            changes.put("keyReasonChangePercentagePoint", a.reasonBreakdown().stream()
                    .filter(s -> s.reason() == p.reason() && s.changePercentagePoint() != null)
                    .map(InsightAnalysis.ReasonShare::changePercentagePoint)
                    .findFirst().orElse(null));
            changes.put("reasonRatioChanges", a.reasonBreakdown().stream()
                    .filter(s -> s.changePercentagePoint() != null)
                    .map(s -> Map.of("reason", InsightLabels.of(s.reason()),
                            "changePercentagePoint", s.changePercentagePoint()))
                    .toList());
            payload.put("changes", changes);
        }
        return payload;
    }

    /** 강한 발견 최대 3개 + BASIC. 라벨은 한국어로 바꾸고, 금액 비교 대상 이름(comparedLabel)을 붙인다. */
    private static List<Map<String, Object>> findingsPayload(InsightAnalysis a) {
        List<InsightAnalysis.Finding> all = a.findings();
        List<InsightAnalysis.Finding> picked = new java.util.ArrayList<>(all.stream()
                .filter(f -> f.type() != InsightAnalysis.FindingType.BASIC).limit(3).toList());
        all.stream().filter(f -> f.type() == InsightAnalysis.FindingType.BASIC).findFirst().ifPresent(picked::add);

        InsightAnalysis.Pattern p = a.pattern();
        String comparedLabel = p.type() == InsightAnalysis.PatternType.ITEM_AMOUNT
                ? "다른 " + InsightLabels.of(p.item()) : "다른 이유로 쓴 소비";
        return picked.stream().map(f -> {
            Map<String, Object> facts = new LinkedHashMap<>();
            f.facts().forEach((k, v) -> facts.put(k, label(v)));
            if (facts.containsKey("othersAverage")) {
                facts.put("comparedLabel", comparedLabel);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", f.type().name());
            m.put("facts", facts);
            return m;
        }).toList();
    }

    private static Object label(Object v) {
        if (v instanceof ExpenseMood m) {
            return InsightLabels.of(m);
        }
        if (v instanceof ExpenseReason r) {
            return InsightLabels.of(r);
        }
        if (v instanceof ExpenseItem i) {
            return InsightLabels.of(i);
        }
        if (v instanceof java.time.DayOfWeek d) {
            return dayLabel(d);
        }
        return v;
    }

    private static String dayLabel(java.time.DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "월요일";
            case TUESDAY -> "화요일";
            case WEDNESDAY -> "수요일";
            case THURSDAY -> "목요일";
            case FRIDAY -> "금요일";
            case SATURDAY -> "토요일";
            case SUNDAY -> "일요일";
        };
    }

    /** 패턴 기록 중 대표 항목 기록 수 (ITEM_AMOUNT 는 전부 대표 항목이다). */
    private static int representativeItemCount(InsightAnalysis a) {
        InsightAnalysis.Pattern p = a.pattern();
        if (p.type() == InsightAnalysis.PatternType.ITEM_AMOUNT) {
            return p.comboCount();
        }
        return a.findings().stream()
                .filter(f -> f.type() == InsightAnalysis.FindingType.BASIC)
                .map(f -> (Object) f.facts().get("representativeItemCount"))
                .filter(java.util.Objects::nonNull)
                .map(o -> ((Number) o).intValue())
                .findFirst().orElse(0);
    }

    // ===== 검증: 빈 문장·길이·입력에 없는 숫자 =====

    InsightText validate(InsightText text, boolean hasPrevious, Set<String> allowedNumbers) {
        if (text == null || isBlank(text.insight()) || isBlank(text.suggestion())
                || isBlank(text.questionForReflection())) {
            throw new InsightGenerationException("Missing required sentence");
        }
        String insight = text.insight().trim();
        String suggestion = text.suggestion().trim();
        String question = text.questionForReflection().trim();
        String comparison = hasPrevious && !isBlank(text.comparison()) ? text.comparison().trim() : null;

        // 숫자는 insight·comparison 에만 쓰고, 입력 통계에 있는 숫자만 허용한다.
        checkSentence("insight", insight, MAX_INSIGHT_LENGTH, ".", true, allowedNumbers);
        checkSentence("suggestion", suggestion, MAX_SUGGESTION_LENGTH, "?", false, allowedNumbers);
        checkSentence("questionForReflection", question, MAX_QUESTION_LENGTH, "?", false, allowedNumbers);
        if (comparison != null) {
            checkSentence("comparison", comparison, MAX_COMPARISON_LENGTH, null, true, allowedNumbers);
        }
        return new InsightText(insight, comparison, suggestion, question);
    }

    /** 길이·끝맺음·금지 표현·숫자를 확인한다. 어기면 재시도 대상이 된다. */
    private static void checkSentence(String field, String sentence, int maxLength, String ending,
                                      boolean numbersAllowed, Set<String> allowedNumbers) {
        if (sentence.length() > maxLength) {
            throw new InsightGenerationException(field + " too long");
        }
        if (ending != null && !sentence.endsWith(ending)) {
            throw new InsightGenerationException(field + " has wrong ending");
        }
        if (LATIN.matcher(sentence.replace("%p", "")).find()) { // 퍼센트포인트 표기(%p)만 허용
            throw new InsightGenerationException(field + " contains latin letters");
        }
        for (String banned : BANNED_EXPRESSIONS) {
            if (sentence.contains(banned)) {
                throw new InsightGenerationException(field + " contains banned expression: " + banned);
            }
        }
        Matcher m = NUMBER.matcher(sentence);
        while (m.find()) {
            String number = normalize(m.group());
            if (!numbersAllowed) {
                throw new InsightGenerationException(field + " must not contain numbers");
            }
            if (!allowedNumbers.contains(number)) {
                throw new InsightGenerationException("Number not in stats: " + number);
            }
        }
    }

    /** 입력 통계에 있는 숫자와 그 반올림·절댓값, 기간 날짜 숫자만 허용한다. */
    Set<String> allowedNumbers(Map<String, Object> stats) {
        Set<String> allowed = new HashSet<>();
        collectNumbers(stats, allowed);
        return allowed;
    }

    @SuppressWarnings("unchecked")
    private void collectNumbers(Object value, Set<String> allowed) {
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(v -> collectNumbers(v, allowed));
        } else if (value instanceof List<?> list) {
            list.forEach(v -> collectNumbers(v, allowed));
        } else if (value instanceof Number n) {
            BigDecimal d = new BigDecimal(n.toString()).abs();
            allowed.add(normalize(d.toPlainString()));
            allowed.add(normalize(d.setScale(0, RoundingMode.HALF_UP).toPlainString()));
            allowed.add(normalize(d.setScale(1, RoundingMode.HALF_UP).toPlainString()));
            allowed.add(normalize(d.setScale(0, RoundingMode.DOWN).toPlainString()));
        } else if (value instanceof String s && s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            LocalDate date = LocalDate.parse(s);
            allowed.add(String.valueOf(date.getYear()));
            allowed.add(String.valueOf(date.getMonthValue()));
            allowed.add(String.valueOf(date.getDayOfMonth()));
        }
    }

    private static String normalize(String number) {
        String plain = number.replace(",", "");
        if (plain.contains(".")) {
            plain = new BigDecimal(plain).stripTrailingZeros().toPlainString();
        }
        return plain;
    }

    /** 기분 라벨별 건수 (건수 많은 순) */
    private static Map<String, Integer> moodCounts(Map<ExpenseMood, Integer> moods) {
        Map<String, Integer> result = new LinkedHashMap<>();
        moods.entrySet().stream()
                .sorted(Map.Entry.<ExpenseMood, Integer>comparingByValue().reversed())
                .forEach(e -> result.put(InsightLabels.of(e.getKey()), e.getValue()));
        return result;
    }

    /** part ÷ whole × 100, 정수 반올림 */
    private static int wholePercent(int part, int whole) {
        return whole == 0 ? 0 : (int) Math.round(part * 100.0 / whole);
    }

    /** part ÷ whole × 100, 소수 첫째 자리 */
    private static double percent(long part, long whole) {
        if (whole == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP).doubleValue();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ===== 대체 문장 (AI 실패 시, 저장하지 않음) =====

    InsightText fallback(PeriodType type, InsightAnalysis a) {
        InsightAnalysis.Pattern p = a.pattern();
        String item = InsightLabels.of(p.item());

        String reason = reasonPhrase(p.reason());
        String insight = fallbackInsight(a, reason, item);

        // AI 지시문의 comparison 규칙과 같은 문장: 전체 지출 증감률 하나만 쓴다.
        String comparison = null;
        if (a.previous() != null) {
            BigDecimal rate = BigDecimal.valueOf(a.previous().totalAmountChangeRate());
            comparison = rate.signum() == 0
                    ? (type == PeriodType.MONTH ? "지난달과 전체 지출이 같아요" : "지난주와 전체 지출이 같아요")
                    : "%s보다 전체 지출이 %s%% %s".formatted(InsightLabels.previousPeriod(type),
                    rate.abs().stripTrailingZeros().toPlainString(),
                    rate.signum() > 0 ? "늘었어요" : "줄었어요");
        }

        String[] advice = fallbackAdvice(p.reason(), p.item());
        return new InsightText(insight, comparison, advice[0], advice[1]);
    }

    /** 가장 강한 발견 하나를 담담한 사실로 쓴다. 해석은 AI 에게만 맡긴다. */
    private static String fallbackInsight(InsightAnalysis a, String reason, String item) {
        InsightAnalysis.Pattern p = a.pattern();
        String subject = reason + " 쓴 소비";
        InsightAnalysis.Finding top = a.findings().isEmpty() ? null : a.findings().getFirst();
        Map<String, Object> f = top == null ? Map.of() : top.facts();
        if (top != null) {
            switch (top.type()) {
                case INTENT_BACKFIRE -> {
                    if (p.reason() == ExpenseReason.DISCOUNT) {
                        return p.type() == InsightAnalysis.PatternType.ITEM_AMOUNT
                                ? "할인을 보고 산 %s는 다른 %s보다 건당 금액이 더 컸어요.".formatted(item, item)
                                : "할인을 보고 산 소비가 다른 이유로 쓴 소비보다 건당 금액이 더 컸어요.";
                    }
                    return "%s %s건 중 %s건이 후회로 남았어요.".formatted(subject, f.get("count"), f.get("regretCount"));
                }
                case REGRET_CONCENTRATION -> {
                    return "이번에 후회한 소비 %s건 중 %s건이 %s였어요.".formatted(
                            f.get("totalRegretCount"), f.get("regretCount"), subject);
                }
                case MOOD_TRIGGER -> {
                    return "%s 소비는 대부분 %s였어요.".formatted(moodPhrase((ExpenseMood) f.get("mood")), subject);
                }
                case DAY_TRIGGER -> {
                    Object day = f.get("day");
                    return "%s %s건 중 %s건이 %s.".formatted(subject, f.get("count"), f.get("dayCount"),
                            day instanceof java.time.DayOfWeek d ? dayLabel(d) + "이었어요" : "주말이었어요");
                }
                case SMALL_ADDS_UP -> {
                    return "%s는 한 번은 작아도 %s번 모이니 %,d원이었어요.".formatted(subject, f.get("count"),
                            ((Number) f.get("total")).longValue());
                }
                case AMOUNT_WEIGHT -> {
                    return "%s는 기록의 %s%%였지만 지출의 %s%%였어요.".formatted(subject, f.get("countShare"),
                            f.get("amountShare"));
                }
                case REGRET_GAP -> {
                    return "%s %s건 중 %s건을 후회했어요.".formatted(subject, f.get("count"), f.get("regretCount"));
                }
                default -> {
                }
            }
        }
        return switch (p.type()) {
            case ITEM_AMOUNT -> "%s 쓴 %s 지출은 다른 %s 지출보다 건당 금액이 높아요.".formatted(reason, item, item);
            case REASON_AMOUNT -> "%s 쓴 소비는 다른 이유로 쓴 소비보다 건당 금액이 높아요.".formatted(reason);
            case REASON_REGRET -> p.comboRegretCount() == p.comboCount()
                    ? "%s 쓴 소비는 모두 후회했어요.".formatted(reason)
                    : "%s 쓴 소비에서 후회한 기록이 많았어요.".formatted(reason);
            case REASON_FREQUENT -> "%s 쓴 소비가 가장 많았어요.".formatted(reason);
        };
    }

    private static String moodPhrase(ExpenseMood mood) {
        return switch (mood) {
            case TIRED -> "지친다고 기록한";
            case DEPRESSED -> "우울하다고 기록한";
            case GOOD -> "기분이 좋다고 기록한";
            case NORMAL -> "보통이라고 기록한";
        };
    }

    private static String reasonPhrase(ExpenseReason reason) {
        return switch (reason) {
            case NEED -> "필요해서";
            case STRESS -> "스트레스 해소로";
            case DISCOUNT -> "할인을 보고";
            case IMPULSE -> "즉흥적으로";
            case REWARD -> "보상으로";
            case HABIT -> "습관처럼";
        };
    }

    /** 기획안 7-2 패턴 그룹별 대체 문장. [suggestion, questionForReflection] */
    private static String[] fallbackAdvice(ExpenseReason reason, ExpenseItem item) {
        if (item == ExpenseItem.DELIVERY && (reason == ExpenseReason.STRESS || reason == ExpenseReason.REWARD)) {
            return new String[]{
                    "배달 앱을 열기 전에 물 한 잔 마시고 5분 뒤에 결정해 보는 건 어때요?",
                    "스트레스나 보상이 필요했던 날, 기분이 나아졌던 다른 방법이 있었나요?"};
        }
        if (item == ExpenseItem.SHOPPING && (reason == ExpenseReason.IMPULSE || reason == ExpenseReason.DISCOUNT)) {
            return new String[]{
                    "결제하기 전에 장바구니에 담아 두고 10분 기다려 보는 건 어때요?",
                    "그 물건을 산 뒤에도 만족스러웠는지 떠올려 볼까요?"};
        }
        if ((item == ExpenseItem.CAFE || item == ExpenseItem.FOOD) && reason == ExpenseReason.HABIT) {
            return new String[]{
                    "결제하기 전에 오늘 이미 쓴 금액을 한 번 확인해 보는 건 어때요?",
                    "습관처럼 쓴 돈 중에 정말 원해서 쓴 건 무엇이었나요?"};
        }
        return new String[]{
                "결제하기 전에 이번 주 남은 예산을 한 번 확인해 보는 건 어때요?",
                "이번 기간 소비 중 가장 만족스러웠던 건 무엇이었나요?"};
    }
}
