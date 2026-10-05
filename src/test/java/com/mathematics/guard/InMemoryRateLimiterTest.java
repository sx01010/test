package com.mathematics.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    private Instant now = Instant.parse("2026-10-05T08:00:10Z");
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };

    @Test
    void rejectsOverLimitAndReportsTimeLeftInWindow() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);
        assertTrue(limiter.acquire("k", 2, MINUTE).allowed());
        assertTrue(limiter.acquire("k", 2, MINUTE).allowed());

        RateLimiter.Decision third = limiter.acquire("k", 2, MINUTE);
        assertFalse(third.allowed());
        assertEquals(Duration.ofSeconds(50), third.retryAfter());
    }

    @Test
    void nextWindowStartsFresh() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);
        limiter.acquire("k", 1, MINUTE);
        assertFalse(limiter.acquire("k", 1, MINUTE).allowed());

        now = now.plusSeconds(50);
        assertTrue(limiter.acquire("k", 1, MINUTE).allowed());
    }

    @Test
    void keysAreCountedIndependently() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);
        limiter.acquire("a", 1, MINUTE);
        assertTrue(limiter.acquire("b", 1, MINUTE).allowed());
    }
}
