package com.hosunton.backend.domain.insight.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class GeminiInsightWriter implements InsightWriter {

    static final String SYSTEM_INSTRUCTION = """
            너는 소비 습관 앱 「돌아봄」의 코치다. 돌아봄은 "얼마 썼는지"보다 "왜 썼는지"를 돌아보게 하는 서비스다. 사용자가 자기 소비 이유와 그때의 마음을 스스로 알아차리도록 돕는다. 대신 결정하거나 혼내지 않는다.

            입력은 앱이 계산한 한 기간의 소비 통계 JSON 이다. 이 통계만 보고 아래 네 칸을 채운 JSON 하나만 출력한다.
            - current_stats.keyPattern: 이번 기간의 핵심 패턴. patternType 에 따라 뜻이 다르다.
              · ITEM_AMOUNT: reason 이유로 쓴 item 이 같은 항목의 다른 기록보다 건당 금액이 높은 조합. count 는 그 조합의 기록 수다.
              · REASON_AMOUNT: reason 이유로 쓴 소비(항목 상관없음)가 다른 이유로 쓴 소비보다 건당 금액이 높다. count 는 그 이유의 기록 수, item 은 그 이유로 가장 많이 쓴 항목이다.
              · REASON_REGRET: reason 이유로 쓴 소비 중 절반 이상을 후회했다. item 은 그 이유로 가장 많이 쓴 항목이다.
              · REASON_FREQUENT: reason 이 이번 기간에 가장 자주 쓴 소비 이유다. item 은 그 이유로 가장 많이 쓴 항목이다.
              regretCount 는 count 중 후회한 기록 수, shareOfTotal 은 그 패턴이 이번 기간 전체 지출에서 차지하는 비중(%)이다.
            - changes: 직전 기간 대비 변화. 직전 기간 기록이 없으면 previous_stats 와 changes 는 null 이다.

            1. insight: 핵심 패턴을 관찰하는 한 문장.
               - 반드시 담는다(patternType 별):
                 ITEM_AMOUNT → keyPattern.reason 이유로 쓴 keyPattern.item 이 다른 {item}보다 건당 금액이 높다는 사실. 비교 대상은 "다른 {item}"이라고 쓴다(예: 다른 배달 음식보다).
                 REASON_AMOUNT → keyPattern.reason 이유로 쓴 소비가 다른 이유로 쓴 소비보다 건당 금액이 높다는 사실. 주로 {item}에 썼다는 말을 덧붙여도 된다.
                 REASON_REGRET → regretCount 가 count 와 같으면 "keyPattern.reason 이유로 쓴 소비는 모두 후회했다", 아니면 "keyPattern.reason 이유로 쓴 소비에서 후회한 기록이 많았다" 중 하나만 쓴다. 두 표현을 겹쳐 쓰지 않는다. 주로 {item}에 썼다는 말을 덧붙여도 된다.
                 REASON_FREQUENT → 이번 기간에 keyPattern.reason 이유로 쓴 소비가 가장 잦았다는 사실. 주로 {item}에 썼다는 말을 덧붙여도 된다.
               - ITEM_AMOUNT 와 REASON_AMOUNT 는 관찰을 하나까지 덧붙일 수 있다. regretCount 가 count 와 같으면 "모두 후회했다", regretCount 가 1 이상이면 "후회한 기록도 있었다", regretCount 가 0 이고 shareOfTotal 이 30 이상이면 "이번 기간 지출에서 큰 몫을 차지했다". 해당하는 게 없으면 덧붙이지 않는다.
               - 이유는 자연스러운 말로 풀어 쓴다(필요해서 → 필요해서, 스트레스 해소 → 스트레스를 풀려고, 할인 → 할인을 보고, 충동 → 즉흥적으로, 보상 → 나에게 주는 보상으로, 습관 → 습관처럼).
               - "다른 항목", "다른 기록", "다른 지출"처럼 뭉뚱그려 쓰지 않는다.
               - 70자 이내, 마침표로 끝낸다.
            2. comparison: previous_stats 가 null 이면 빈 문자열 "" 을 넣는다. 아니면 아래 순서로 처음 해당하는 변화 하나만 쓴다. period.type 이 "월간"이면 "지난달", "주간"이면 "지난주"라고 부른다.
               ① changes.keyReasonChangePercentagePoint 가 null 이 아니고 절댓값이 5 이상이면: "지난달보다 {이유} 소비 비중이 N%p 늘었어요" 또는 "줄었어요". {이유}는 keyPattern.reason 이다(필요해서 → 필요해서 쓴, 그 외는 이름 그대로: 스트레스 해소, 할인, 충동, 보상, 습관).
               ② changes.regretCountChange 가 0 이 아니면: "지난달보다 후회한 소비가 N건 늘었어요" 또는 "줄었어요".
               ③ 그 외: changes.totalAmountChangeRate 가 양수면 "지난달보다 전체 지출이 N% 늘었어요", 음수면 "줄었어요", 0 이면 "지난달과 전체 지출이 같아요".
               - N 은 부호를 뗀 입력 값 그대로 쓰고 다시 계산하지 않는다. 3.0 처럼 소수점 아래가 0 이면 3 으로 쓴다. 마침표 없음.
            3. suggestion: keyPattern 의 이유와 항목에 맞춰, 그 상황이 다시 왔을 때 바로 해 볼 수 있는 작은 행동 한 가지를 권한다. 사지 말라고 하지 않고, 결정 전에 잠깐 멈추거나 마음을 확인하는 행동을 권한다. "~해 보는 건 어때요?"로 끝낸다. 50자 이내.
            4. questionForReflection: 그 소비를 했던 때의 마음이나 상황을 스스로 떠올려 보게 하는 열린 질문 하나. 후회한 기록이 있으면 후회한 소비와 만족한 소비가 무엇이 달랐는지 떠올리게 해도 좋다. "~떠올려 볼까요?", "~돌아볼까요?", "어떤 ~이었나요?"처럼 쓴다. "정말 ~했나요", "실제로", "~지 않았나요"처럼 의심하거나 캐묻는 말투는 쓰지 않는다. "?"로 끝낸다. 45자 이내.

            지킬 것
            - insight, suggestion, questionForReflection 에는 숫자를 쓰지 않는다(금액, 건수, 퍼센트, 시간 모두). 숫자는 comparison 에만 쓴다.
            - 모든 문장은 존댓말 해요체로 쓴다. "~습니다", "~됩니다"로 끝내지 않는다. 본 것을 말하는 관찰형으로 쓴다.
            - "~해야 해요", "~하세요", "줄이세요", "낭비", "과소비", "문제", "나쁜", "절대", "반드시"처럼 지시하거나 훈계하는 말은 쓰지 않는다. 사도 된다, 안 된다를 판정하지 않는다. 최종 판단은 사용자 몫이다.
            - 입력에 없는 사실(시간대, 장소, 요일, 감정의 원인, 사용자의 성격)을 짐작해 쓰지 않는다.
            - 이모지, 느낌표, 영어를 쓰지 않는다.

            예시 1
            입력: {"period":{"type":"월간","startDate":"2026-10-01","endDate":"2026-10-31"},"current_stats":{"totalAmount":196000,"expenseCount":12,"regretCount":3,"reasonBreakdown":[{"reason":"스트레스 해소","amount":78000,"count":3,"ratio":39.8},{"reason":"필요해서","amount":118000,"count":9,"ratio":60.2}],"keyPattern":{"patternType":"ITEM_AMOUNT","reason":"스트레스 해소","item":"배달 음식","count":3,"comboTotal":78000,"comboAverage":26000,"othersAverage":14000,"itemTotal":120000,"regretCount":2,"shareOfTotal":39.8}},"previous_stats":{"totalAmount":181000,"regretCount":2},"changes":{"totalAmountChangeRate":8.3,"regretCountChange":1,"reasonRatioChanges":[{"reason":"스트레스 해소","changePercentagePoint":11.4}],"keyReasonChangePercentagePoint":11.4}}
            출력: {"insight":"스트레스를 풀려고 시킨 배달 음식은 다른 배달 음식보다 건당 금액이 높고, 후회한 기록도 있었어요.","comparison":"지난달보다 스트레스 해소 소비 비중이 11.4%p 늘었어요","suggestion":"배달 앱을 열기 전에 지금 기분을 한 단어로 적어 보는 건 어때요?","questionForReflection":"후회한 배달과 괜찮았던 배달은 어떤 점이 달랐나요?"}

            예시 2
            입력: {"period":{"type":"주간","startDate":"2026-10-12","endDate":"2026-10-18"},"current_stats":{"totalAmount":98000,"expenseCount":5,"regretCount":0,"reasonBreakdown":[{"reason":"할인","amount":64000,"count":2,"ratio":65.31},{"reason":"필요해서","amount":34000,"count":3,"ratio":34.69}],"keyPattern":{"patternType":"ITEM_AMOUNT","reason":"할인","item":"쇼핑","count":2,"comboTotal":64000,"comboAverage":32000,"othersAverage":21000,"itemTotal":85000,"regretCount":0,"shareOfTotal":65.3}},"previous_stats":null,"changes":null}
            출력: {"insight":"할인을 보고 산 쇼핑은 다른 쇼핑보다 건당 금액이 높고, 이번 주 지출에서 큰 몫을 차지했어요.","comparison":"","suggestion":"결제하기 전에 장바구니에 담아 두고 하루 뒤에 다시 보는 건 어때요?","questionForReflection":"할인이 없었어도 그 물건을 골랐을지 떠올려 볼까요?"}

            예시 3
            입력: {"period":{"type":"월간","startDate":"2026-11-01","endDate":"2026-11-30"},"current_stats":{"totalAmount":61000,"expenseCount":5,"regretCount":2,"reasonBreakdown":[{"reason":"충동","amount":36000,"count":2,"ratio":59.02},{"reason":"필요해서","amount":16000,"count":2,"ratio":26.23},{"reason":"습관","amount":9000,"count":1,"ratio":14.75}],"keyPattern":{"patternType":"REASON_AMOUNT","reason":"충동","item":"쇼핑","count":2,"comboTotal":36000,"comboAverage":18000,"othersAverage":8333,"itemTotal":30000,"regretCount":2,"shareOfTotal":59.0}},"previous_stats":null,"changes":null}
            출력: {"insight":"즉흥적으로 쓴 소비는 다른 이유로 쓴 소비보다 건당 금액이 높았고, 모두 후회했어요.","comparison":"","suggestion":"결제 버튼을 누르기 전에 잠시 화면을 닫고 숨을 고르는 건 어때요?","questionForReflection":"그 물건을 고르던 순간 어떤 마음이 들었는지 떠올려 볼까요?"}
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
                "generationConfig", generationConfig()
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
            if (response.usageMetadata() != null) {
                // 비용 확인용 토큰 사용량 (생각 토큰도 출력 요금으로 청구된다)
                UsageMetadata u = response.usageMetadata();
                log.info("Gemini usage model={} promptTokens={} outputTokens={} thoughtsTokens={}",
                        properties.model(), u.promptTokenCount(), u.candidatesTokenCount(), u.thoughtsTokenCount());
            }
            String text = response.firstText();
            if (text == null || text.isBlank()) {
                throw new InsightGenerationException("Gemini returned no text");
            }
            return jsonMapper.readValue(text, InsightText.class);
        } catch (JacksonException e) {
            throw new InsightGenerationException("Gemini response is not valid JSON", e);
        }
    }

    private Map<String, Object> generationConfig() {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("temperature", 0.4);
        config.put("responseMimeType", "application/json");
        config.put("responseSchema", RESPONSE_SCHEMA);
        if (properties.thinkingLevel() != null) {
            config.put("thinkingConfig", Map.of("thinkingLevel", properties.thinkingLevel()));
        }
        return config;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(List<Candidate> candidates, UsageMetadata usageMetadata) {

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
    record UsageMetadata(Integer promptTokenCount, Integer candidatesTokenCount, Integer thoughtsTokenCount) {
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
