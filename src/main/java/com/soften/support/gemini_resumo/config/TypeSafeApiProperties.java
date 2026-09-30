package com.soften.support.gemini_resumo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "typesafe.api")
public class TypeSafeApiProperties {

    public static final int HARD_MAX_CONVERSATION_CHARS = 20_000;

    private String key;
    private String model = "jev-latest";
    private String systemOneUrl = "https://api.typesafe.ai/v1/systemone";
    private int connectTimeoutMillis = 1000;
    private int readTimeoutMillis = 2500;
    private int maxConversationChars = 20000;
    private int rateLimitPerMinute = 120;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSystemOneUrl() {
        return systemOneUrl;
    }

    public void setSystemOneUrl(String systemOneUrl) {
        this.systemOneUrl = systemOneUrl;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public int getMaxConversationChars() {
        return maxConversationChars;
    }

    public void setMaxConversationChars(int maxConversationChars) {
        this.maxConversationChars = maxConversationChars;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public int getSafeConnectTimeoutMillis() {
        return Math.max(connectTimeoutMillis, 100);
    }

    public int getSafeReadTimeoutMillis() {
        return Math.max(readTimeoutMillis, 250);
    }

    public int getSafeMaxConversationChars() {
        return Math.min(
                Math.max(maxConversationChars, 1000),
                HARD_MAX_CONVERSATION_CHARS
        );
    }

    public int getSafeRateLimitPerMinute() {
        return Math.max(rateLimitPerMinute, 1);
    }

    public boolean isConfigured() {
        return key != null && !key.isBlank();
    }
}
