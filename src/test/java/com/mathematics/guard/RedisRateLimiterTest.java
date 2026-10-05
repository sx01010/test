package com.mathematics.guard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 真 Redis 上验证 Lua 脚本的计数与过期。没设 REDIS_HOST 就跳过，跑法：
 *
 * <pre>REDIS_HOST=127.0.0.1 mvn -o -B test -Dtest=RedisRateLimiterTest</pre>
 */
@EnabledIfEnvironmentVariable(named = "REDIS_HOST", matches = ".+",
        disabledReason = "只在本机有 Redis 且设置了 REDIS_HOST 时运行")
class RedisRateLimiterTest {

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;

    @BeforeEach
    void connect() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(System.getenv("REDIS_HOST"), 6379));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @AfterEach
    void close() {
        factory.destroy();
    }

    @Test
    void countsAcrossCallsAndSetsExpiry() {
        RedisRateLimiter limiter = new RedisRateLimiter(redis, Clock.systemUTC());
        String key = "test:" + UUID.randomUUID();
        assertTrue(limiter.acquire(key, 2, Duration.ofMinutes(1)).allowed());
        assertTrue(limiter.acquire(key, 2, Duration.ofMinutes(1)).allowed());
        assertFalse(limiter.acquire(key, 2, Duration.ofMinutes(1)).allowed());

        Long ttl = redis.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection ->
                connection.keyCommands().pTtl(redis.keys("rl:" + key + ":*").iterator().next().getBytes()));
        assertTrue(ttl != null && ttl > 0 && ttl <= 60_000, "计数 key 必须带过期时间，否则会永久限流");
    }

    @Test
    void failsOpenWhenRedisIsUnreachable() {
        LettuceConnectionFactory dead = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
        dead.afterPropertiesSet();
        try {
            StringRedisTemplate deadRedis = new StringRedisTemplate(dead);
            deadRedis.afterPropertiesSet();
            assertTrue(new RedisRateLimiter(deadRedis, Clock.systemUTC())
                    .acquire("k", 1, Duration.ofMinutes(1)).allowed());
        } finally {
            dead.destroy();
        }
    }
}
