package com.soften.support.gemini_resumo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import static java.lang.Double.isFinite;

@Component
@ConfigurationProperties(prefix = "gemini.api")
public class GeminiApiProperties {

    private String smartReplyModel = "";
    private boolean smartReplyPremiumEnabled = false;
    private int smartReplyReadTimeoutMillis = 4000;

    public String getSmartReplyModel() { return smartReplyModel; }
    public void setSmartReplyModel(String value) { smartReplyModel = value; }
    public boolean isSmartReplyPremiumEnabled() { return smartReplyPremiumEnabled; }
    public void setSmartReplyPremiumEnabled(boolean value) { smartReplyPremiumEnabled = value; }
    public int getSmartReplyReadTimeoutMillis() { return smartReplyReadTimeoutMillis; }
    public void setSmartReplyReadTimeoutMillis(int value) { smartReplyReadTimeoutMillis = value; }
    public int getSafeSmartReplyReadTimeoutMillis() {
        // Two attempts + connections + backoff remain below the extension's 15 s deadline.
        return Math.max(1000, Math.min(smartReplyReadTimeoutMillis, 5000));
    }
    public String resolveSmartReplyModel() {
        String configured = smartReplyModel == null ? "" : smartReplyModel.trim();
        if (configured.isEmpty() || configured.equals(model)) return model;
        if (!configured.equals("gemini-2.5-flash-lite") && !configured.equals("gemini-3.8-flash")) {
            throw new IllegalStateException("Unsupported Smart Reply model configuration.");
        }
        return configured.equals("gemini-3.8-flash") && !smartReplyPremiumEnabled ? model : configured;
    }

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
