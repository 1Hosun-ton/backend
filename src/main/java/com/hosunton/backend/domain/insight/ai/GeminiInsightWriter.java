package com.hosunton.backend.domain.insight.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Gemini generateContent REST 호출. 구조화 JSON 출력(responseSchema)으로 문장 4개를 받는다.
 */
public class GeminiInsightWriter implements InsightWriter {

    static final String SYSTEM_INSTRUCTION = """
            너는 소비 습관 앱 「돌아봄」의 코치다. 사용자가 자기 소비 습관을 스스로 알아차리고 판단하도록 돕는다. 대신 결정하거나 혼내지 않는다.

            입력은 앱이 계산한 한 기간의 소비 통계 JSON 이다. 이 통계만 보고 아래 네 칸을 채운 JSON 하나만 출력한다.

            1. insight: current_stats.keyPattern 을 한 문장으로 풀어 쓴다. keyPattern.reason 이유로 쓴 keyPattern.item 의 건당 금액이 같은 항목의 다른 기록보다 높다는 사실을 관찰하듯 말한다. 이유는 자연스러운 말로 풀어 쓴다(필요해서 → 필요해서, 스트레스 해소 → 스트레스 해소로, 할인 → 할인을 보고, 충동 → 즉흥적으로, 보상 → 보상으로, 습관 → 습관처럼). 비교 대상은 반드시 "다른 {item}"이라고 쓴다(예: 다른 배달 음식보다). "다른 항목", "다른 기록", "다른 지출"이라고 쓰지 않는다. 46자 이내, 마침표로 끝낸다.
            2. comparison: previous_stats 가 null 이면 빈 문자열 "" 을 넣는다. 아니면 changes.totalAmountChangeRate 값 하나만 쓴다. period.type 이 "월간"이면 "지난달", "주간"이면 "지난주"라고 부른다. 값이 양수면 "지난달보다 전체 지출이 N% 늘었어요", 음수면 "지난달보다 전체 지출이 N% 줄었어요"(N 은 부호를 뗀 값), 0 이면 "지난달과 전체 지출이 같아요"(주간이면 "지난주와 전체 지출이 같아요")로 쓴다. N 은 입력 값 그대로 쓰고 다시 계산하지 않는다. 마침표 없음. 후회한 소비나 이유별 비중은 여기에 쓰지 않는다.
            3. suggestion: keyPattern 에 맞춰 오늘 바로 해 볼 수 있는 작은 행동 한 가지를 권한다. "~해 보는 건 어때요?"로 끝낸다. 40자 안팎으로 짧게 쓰고 45자를 넘기지 않는다.
            4. questionForReflection: 사용자가 그때의 마음이나 상황을 스스로 떠올려 보게 하는 질문 하나. "~떠올려 볼까요?", "~돌아볼까요?", "어떤 ~이었나요?"처럼 열린 질문으로 쓴다. "정말 ~했나요", "실제로", "~지 않았나요"처럼 의심하거나 캐묻는 말투는 쓰지 않는다. "?"로 끝낸다. 40자 이내.

            지킬 것
            - insight, suggestion, questionForReflection 에는 숫자를 쓰지 않는다(금액, 건수, 퍼센트, 시간 모두). 숫자는 comparison 에만 쓴다.
            - 모든 문장은 존댓말 해요체로 쓴다("~해요", "~나타나요"). "~습니다", "~됩니다"로 끝내지 않는다. 본 것을 말하는 관찰형으로 쓴다.
            - "~해야 해요", "줄이세요", "낭비", "과소비", "문제", "나쁜", "절대", "반드시"처럼 단정하거나 훈계하는 말은 쓰지 않는다. 사도 된다, 안 된다를 판정하지 않는다. 최종 판단은 사용자 몫이다.
            - 입력에 없는 사실(시간대, 장소, 감정의 원인, 사용자의 성격)을 짐작해 쓰지 않는다.
            - 이모지, 느낌표, 영어를 쓰지 않는다.

            예시
            입력: {"period":{"type":"주간","startDate":"2026-10-12","endDate":"2026-10-18"},"current_stats":{"totalAmount":98000,"expenseCount":5,"regretCount":1,"reasonBreakdown":[{"reason":"할인","amount":64000,"count":2,"ratio":65.31},{"reason":"필요해서","amount":34000,"count":3,"ratio":34.69}],"keyPattern":{"reason":"할인","item":"쇼핑","count":2,"comboTotal":64000,"comboAverage":32000,"othersAverage":21000,"itemTotal":85000}},"previous_stats":{"totalAmount":83000,"regretCount":1},"changes":{"totalAmountChangeRate":18.1,"regretCountChange":0,"reasonRatioChanges":[{"reason":"할인","changePercentagePoint":12.3},{"reason":"필요해서","changePercentagePoint":-12.3}]}}
            출력: {"insight":"할인을 보고 산 쇼핑은 다른 쇼핑보다 건당 금액이 높게 나타나요.","comparison":"지난주보다 전체 지출이 18.1% 늘었어요","suggestion":"결제하기 전에 장바구니에 담아 두고 하루 뒤에 다시 보는 건 어때요?","questionForReflection":"할인이 없었어도 그 물건을 샀을지 떠올려 볼까요?"}
            """;

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "insight", Map.of("type", "STRING"),
                    "comparison", Map.of("type", "STRING"),
                    "suggestion", Map.of("type", "STRING"),
                    "questionForReflection", Map.of("type", "STRING")
            ),
            "required", List.of("insight", "comparison", "suggestion", "questionForReflection"),
            "propertyOrdering", List.of("insight", "comparison", "suggestion", "questionForReflection")
    );

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final JsonMapper jsonMapper;

    public GeminiInsightWriter(RestClient restClient, GeminiProperties properties, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean isAvailable() {
        return properties.hasApiKey();
    }

    @Override
    public InsightText write(String statsJson) {
        if (!isAvailable()) {
            throw new InsightGenerationException("Gemini API key is not configured");
        }

        String requestBody = jsonMapper.writeValueAsString(Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", statsJson)))),
                "generationConfig", Map.of(
                        "temperature", 0.4,
                        "responseMimeType", "application/json",
                        "responseSchema", RESPONSE_SCHEMA
                )
        ));

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(properties.baseUrl() + "/v1beta/models/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            // 메시지에 키가 들어가지 않도록 원인 클래스만 남긴다.
            throw new InsightGenerationException("Gemini request failed: " + e.getClass().getSimpleName(), e);
        }

        try {
            GeminiResponse response = jsonMapper.readValue(responseBody, GeminiResponse.class);
            String text = response.firstText();
            if (text == null || text.isBlank()) {
                throw new InsightGenerationException("Gemini returned no text");
            }
            return jsonMapper.readValue(text, InsightText.class);
        } catch (JacksonException e) {
            throw new InsightGenerationException("Gemini response is not valid JSON", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(List<Candidate> candidates) {

        String firstText() {
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }
            Content content = candidates.getFirst().content();
            if (content == null || content.parts() == null || content.parts().isEmpty()) {
                return null;
            }
            return content.parts().getFirst().text();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(List<Part> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text) {
    }
}
