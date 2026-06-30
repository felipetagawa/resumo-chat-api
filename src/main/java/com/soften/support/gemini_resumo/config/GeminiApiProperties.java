package com.soften.support.gemini_resumo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import static java.lang.Double.isFinite;

@Component
@ConfigurationProperties(prefix = "gemini.api")
public class GeminiApiProperties {

    private String key;
    private String model = "gemini-2.5-flash-lite";
    private String generateContentBaseUrl = "https://generativelanguage.googleapis.com/v1/models";
    private int maxAttempts = 4;
    private long initialDelayMillis = 500L;
    private double backoffMultiplier = 2.0d;
    private long maxRetryAfterMillis = 5000L;
    private int connectTimeoutMillis = 2000;
    private int readTimeoutMillis = 8000;

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

    public String getGenerateContentBaseUrl() {
        return generateContentBaseUrl;
    }

    public void setGenerateContentBaseUrl(String generateContentBaseUrl) {
        this.generateContentBaseUrl = generateContentBaseUrl;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getInitialDelayMillis() {
        return initialDelayMillis;
    }

    public void setInitialDelayMillis(long initialDelayMillis) {
        this.initialDelayMillis = initialDelayMillis;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public void setBackoffMultiplier(double backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }

    public long getMaxRetryAfterMillis() {
        return maxRetryAfterMillis;
    }

    public void setMaxRetryAfterMillis(long maxRetryAfterMillis) {
        this.maxRetryAfterMillis = maxRetryAfterMillis;
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

    public int getSafeMaxAttempts() {
        return Math.max(maxAttempts, 1);
    }

    public long getSafeInitialDelayMillis() {
        return Math.max(initialDelayMillis, 0L);
    }

    public double getSafeBackoffMultiplier() {
        if (!isFinite(backoffMultiplier) || backoffMultiplier <= 0d) {
            return 2.0d;
        }
        return backoffMultiplier;
    }

    public long getSafeMaxRetryAfterMillis() {
        return Math.max(maxRetryAfterMillis, 0L);
    }
}
