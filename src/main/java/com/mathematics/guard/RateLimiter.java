package com.mathematics.guard;

import java.time.Duration;

/**
 * 计数存储。单实例用内存实现就够；多实例部署必须换成 Redis，否则每台机器各算各的，
 * 实际额度会变成配置值乘以实例数。
 */
public interface RateLimiter {

    /**
     * @return 放行时为 {@link Decision#allowed()}；拒绝时带上距离窗口结束还有多久
     */
    Decision acquire(String key, int limit, Duration window);

    record Decision(boolean allowed, Duration retryAfter) {

        public static Decision allow() {
            return new Decision(true, Duration.ZERO);
        }

        public static Decision reject(Duration retryAfter) {
            return new Decision(false, retryAfter);
        }
    }
}
