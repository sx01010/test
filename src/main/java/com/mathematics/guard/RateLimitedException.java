package com.mathematics.guard;

import java.time.Duration;

import com.mathematics.support.ApiException;
import com.mathematics.support.ErrorCode;

public class RateLimitedException extends ApiException {

    private final Duration retryAfter;

    public RateLimitedException(Duration retryAfter) {
        super(ErrorCode.RATE_LIMITED, "操作太频繁，请 " + seconds(retryAfter) + " 秒后再试");
        this.retryAfter = retryAfter;
    }

    /** Retry-After 头只接受整秒，向上取整，免得客户端恰好卡在窗口边界前重试又被拒。 */
    public long retryAfterSeconds() {
        return seconds(retryAfter);
    }

    private static long seconds(Duration duration) {
        return Math.max(1, (duration.toMillis() + 999) / 1000);
    }
}
