package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.config.TypeSafeApiProperties;
import org.springframework.stereotype.Service;

@Service
public class ClassificationRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final TypeSafeApiProperties properties;
    private long windowStartedAt = System.currentTimeMillis();
    private int requestsInWindow = 0;

    public ClassificationRateLimiter(TypeSafeApiProperties properties) {
        this.properties = properties;
    }

    public synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();

        if (now - windowStartedAt >= WINDOW_MILLIS) {
            windowStartedAt = now;
            requestsInWindow = 0;
        }

        if (requestsInWindow >= properties.getSafeRateLimitPerMinute()) {
            return false;
        }

        requestsInWindow++;
        return true;
    }
}
