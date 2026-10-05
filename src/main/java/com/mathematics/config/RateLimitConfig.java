package com.mathematics.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.mathematics.guard.InMemoryRateLimiter;
import com.mathematics.guard.RateLimiter;
import com.mathematics.guard.RedisRateLimiter;

@Configuration
public class RateLimitConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    @ConditionalOnProperty(name = "mathematics.rate-limit.store", havingValue = "redis")
    public RateLimiter redisRateLimiter(StringRedisTemplate redis, Clock clock) {
        return new RedisRateLimiter(redis, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "mathematics.rate-limit.store", havingValue = "memory", matchIfMissing = true)
    public RateLimiter inMemoryRateLimiter(Clock clock) {
        return new InMemoryRateLimiter(clock);
    }
}
