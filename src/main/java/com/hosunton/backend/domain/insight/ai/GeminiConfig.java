package com.hosunton.backend.domain.insight.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiConfig {

    @Bean
    public InsightWriter insightWriter(RestClient.Builder restClientBuilder, GeminiProperties properties,
                                       JsonMapper jsonMapper) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requestFactory.setReadTimeout(properties.timeout());

        RestClient restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .build();
        return new GeminiInsightWriter(restClient, properties, jsonMapper);
    }
}
