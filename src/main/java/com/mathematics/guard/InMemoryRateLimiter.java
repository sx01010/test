package com.mathematics.guard;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程内固定窗口计数。窗口编号拼进 key，过期的桶在计数表变大时顺手清掉。
 */
public class InMemoryRateLimiter implements RateLimiter {

    private static final int SWEEP_THRESHOLD = 10_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Decision acquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long windowMillis = window.toMillis();
        long windowStart = now - Math.floorMod(now, windowMillis);
        long windowEnd = windowStart + windowMillis;

        if (buckets.size() > SWEEP_THRESHOLD) {
            buckets.values().removeIf(bucket -> bucket.expiresAt <= now);
        }
        Bucket bucket = buckets.computeIfAbsent(key + ':' + windowStart, k -> new Bucket(windowEnd));
        if (bucket.count.incrementAndGet() <= limit) {
            return Decision.allow();
        }
        return Decision.reject(Duration.ofMillis(windowEnd - now));
    }

    private static final class Bucket {
        private final long expiresAt;
        private final AtomicInteger count = new AtomicInteger();

        private Bucket(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
