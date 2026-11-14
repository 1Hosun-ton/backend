package com.hosunton.backend.domain.insight.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Gemini API 설정. API 키는 환경 변수 GEMINI_API_KEY로만 받는다 (커밋 금지).
 * 키가 비어 있으면 LLM을 호출하지 않고 대체 문장을 쓴다.
 */
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        String apiKey,
        String model,
        String baseUrl,
        Duration timeout,
        /** 생각 수준(low/medium/high). 비우면 모델 기본값. 낮출수록 빠르고 싸다. */
        String thinkingLevel
) {

    public GeminiProperties {
        if (model == null || model.isBlank()) {
            model = "gemini-3.5-flash-lite";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://generativelanguage.googleapis.com";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(15);
        }
        if (thinkingLevel != null && thinkingLevel.isBlank()) {
            thinkingLevel = null;
        }
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
