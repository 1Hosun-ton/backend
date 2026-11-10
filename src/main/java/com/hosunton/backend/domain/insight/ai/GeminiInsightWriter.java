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
            당신은 사용자를 비난하지 않는 소비 습관 코치입니다.
            입력으로 받은 집계 통계 JSON만 보고, 사용자가 스스로 소비 습관을 알아차리도록 돕는 문장을 씁니다.
            규칙:
            - 입력에 없는 수치는 절대 쓰지 마세요. 비율·증감률을 직접 계산하지 말고 입력의 값을 그대로 쓰세요.
            - insight: 핵심 패턴(keyPattern)을 관찰형 한 문장으로 설명하세요.
            - comparison: previous_stats가 null이면 빈 문자열로 두세요. 있으면 changes의 값으로 지난 기간 대비 변화를 한 문장으로 쓰세요.
            - suggestion: 대안 행동 한 가지만, "~은 어때요?" 같은 권유형으로 쓰세요.
            - questionForReflection: 사용자가 스스로 생각해 볼 질문 하나를 쓰세요.
            - 존댓말과 관찰형 표현을 쓰고, 단정·훈계·비난 표현은 쓰지 마세요. 최종 판단은 사용자에게 맡기세요.
            - 각 문장은 100자 이내의 한국어로 쓰세요.
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
