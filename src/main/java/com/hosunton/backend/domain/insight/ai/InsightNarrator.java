package com.hosunton.backend.domain.insight.ai;

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
import java.util.ArrayList;
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
    private static final int MAX_TEXT_LENGTH = 200;
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
        keyPattern.put("reason", InsightLabels.of(p.reason()));
        keyPattern.put("item", InsightLabels.of(p.item()));
        keyPattern.put("count", p.comboCount());
        keyPattern.put("comboTotal", p.comboTotal());
        keyPattern.put("comboAverage", p.comboAverage());
        keyPattern.put("othersAverage", p.othersAverage());
        keyPattern.put("itemTotal", p.itemTotal());
        current.put("keyPattern", keyPattern);
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
            changes.put("reasonRatioChanges", a.reasonBreakdown().stream()
                    .filter(s -> s.changePercentagePoint() != null)
                    .map(s -> Map.of("reason", InsightLabels.of(s.reason()),
                            "changePercentagePoint", s.changePercentagePoint()))
                    .toList());
            payload.put("changes", changes);
        }
        return payload;
    }

    // ===== 검증: 빈 문장·길이·입력에 없는 숫자 =====

    InsightText validate(InsightText text, boolean hasPrevious, Set<String> allowedNumbers) {
        if (text == null || isBlank(text.insight()) || isBlank(text.suggestion())
                || isBlank(text.questionForReflection())) {
            throw new InsightGenerationException("Missing required sentence");
        }
        String comparison = hasPrevious && !isBlank(text.comparison()) ? text.comparison().trim() : null;
        List<String> sentences = new ArrayList<>(List.of(text.insight(), text.suggestion(), text.questionForReflection()));
        if (comparison != null) {
            sentences.add(comparison);
        }
        for (String sentence : sentences) {
            if (sentence.length() > MAX_TEXT_LENGTH) {
                throw new InsightGenerationException("Sentence too long");
            }
            Matcher m = NUMBER.matcher(sentence);
            while (m.find()) {
                String number = normalize(m.group());
                if (!allowedNumbers.contains(number)) {
                    throw new InsightGenerationException("Number not in stats: " + number);
                }
            }
        }
        return new InsightText(text.insight().trim(), comparison, text.suggestion().trim(),
                text.questionForReflection().trim());
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ===== 대체 문장 (AI 실패 시, 저장하지 않음) =====

    InsightText fallback(PeriodType type, InsightAnalysis a) {
        InsightAnalysis.Pattern p = a.pattern();
        String reason = InsightLabels.of(p.reason());
        String item = InsightLabels.of(p.item());

        String insight = "'%s' 이유로 쓴 %s 지출은 같은 항목의 다른 지출보다 건당 금액이 높게 나타나요."
                .formatted(reason, item);

        String comparison = null;
        if (a.previous() != null) {
            int regretChange = a.regretCount() - a.previous().regretCount();
            String previous = InsightLabels.previousPeriod(type);
            comparison = regretChange == 0
                    ? "%s와 후회한 소비 건수가 같아요.".formatted(previous)
                    : "%s보다 후회한 소비가 %d건 %s.".formatted(previous, Math.abs(regretChange),
                    regretChange > 0 ? "늘었어요" : "줄었어요");
        }

        String suggestion = "다음에 %s 결제 전에 지금 쓰는 이유를 한 번 떠올려 보는 건 어때요?".formatted(item);
        String question = "'%s' 이유로 %s을(를) 선택하게 되는 순간은 언제일까요?".formatted(reason, item);
        return new InsightText(insight, comparison, suggestion, question);
    }
}
