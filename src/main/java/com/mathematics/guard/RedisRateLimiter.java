package com.mathematics.guard;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 多实例共享计数。INCR 和设置过期必须原子，否则 INCR 成功、PEXPIRE 前进程挂掉，
 * 这个 key 就永不过期，对应的人永远被限流。
 *
 * <p>Redis 不可用时放行并打 WARN：限流是保护措施，不该因为它挂了就让所有人登录不了。
 */
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private static final DefaultRedisScript<Long> INCR_WITH_EXPIRE = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    public RedisRateLimiter(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public Decision acquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long windowMillis = window.toMillis();
        long windowStart = now - Math.floorMod(now, windowMillis);
        long windowEnd = windowStart + windowMillis;
        Long count;
        try {
            count = redis.execute(INCR_WITH_EXPIRE, List.of("rl:" + key + ':' + windowStart),
                    String.valueOf(windowMillis));
        } catch (RuntimeException ex) {
            log.warn("限流计数写 Redis 失败，本次放行：{}", ex.getMessage());
            return Decision.allow();
        }
        if (count == null || count <= limit) {
            return Decision.allow();
        }
        return Decision.reject(Duration.ofMillis(windowEnd - now));
    }
}
