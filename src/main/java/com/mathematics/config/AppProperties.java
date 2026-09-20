package com.mathematics.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mathematics")
public record AppProperties(Practice practice, Auth auth, Dev dev) {

    /**
     * @param masteryThreshold R12：连续做对多少次自动移出错题本
     */
    public record Practice(int masteryThreshold) {
    }

    /**
     * @param maxLoginFailures R02：连续失败多少次锁定账号
     * @param lockDuration     锁定时长
     */
    public record Auth(Duration accessTokenTtl, Duration refreshTokenTtl, int maxLoginFailures, Duration lockDuration) {
    }

    /**
     * @param seedDemoUser 开发环境自动建一个可登录的演示账号，mysql profile 关闭
     */
    public record Dev(boolean seedDemoUser) {
    }
}
