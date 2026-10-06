package com.soften.support.gemini_resumo.service;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
// Endpoint-specific global budget per instance. No persistence or client IP headers.
@Service
public class SmartReplyRateLimiter {
    private final int limit;
    private final LongSupplier clock;
    private long started;
    private int used;
    @Autowired
    public SmartReplyRateLimiter(@Value("${smart-reply.rate-limit-per-minute:30}") int limit) {
        this(limit, System::currentTimeMillis);
    }
    SmartReplyRateLimiter(int limit, LongSupplier clock) {
        this.limit = Math.max(1, limit); this.clock = clock; started = clock.getAsLong();
    }
    public synchronized boolean tryAcquire() {
        long now = clock.getAsLong();
        if (now - started >= 60000) { started = now; used = 0; }
        if (used >= limit) return false;
        used++; return true;
    }
}
