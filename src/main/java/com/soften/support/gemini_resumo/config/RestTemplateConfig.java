package com.soften.support.gemini_resumo.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    @Primary
    public RestTemplate geminiRestTemplate(RestTemplateBuilder builder, GeminiApiProperties properties) {
        return builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .build();
    }
    public RestTemplate smartReplyRestTemplate(RestTemplateBuilder builder) {
        return smartReplyRestTemplate(builder, new GeminiApiProperties());
    }

    @Bean
    public RestTemplate smartReplyRestTemplate(RestTemplateBuilder builder, GeminiApiProperties properties) {
        return builder.setConnectTimeout(Duration.ofMillis(1500)).setReadTimeout(Duration.ofMillis(properties.getSafeSmartReplyReadTimeoutMillis())).build();
    }
}
