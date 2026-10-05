package com.mathematics.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mathematics")
public record AppProperties(Practice practice, Auth auth, Dev dev, Material material, RateLimit rateLimit) {

    /**
     * @param enabled           总开关。集成测试默认关掉，否则同一个 MockMvc 连续注册几十个用户会撞上 IP 限额
     * @param store             memory 或 redis。多实例部署必须用 redis，否则额度按实例数翻倍
     * @param trustForwardedFor 前面有可信反向代理时才打开，直连时这个头谁都能伪造
     */
    public record RateLimit(boolean enabled, String store, boolean trustForwardedFor) {
    }

    /**
     * @param masteryThreshold R12：连续做对多少次自动移出错题本
     */
    public record Practice(int masteryThreshold) {
    }

    /**
     * @param maxLoginFailures  R02：连续失败多少次锁定账号
     * @param lockDuration      锁定时长
     * @param resetCodeTtl      R20：验证码有效期，规格定 10 分钟
     * @param resetCodeCooldown R20：同一账号多久才能再要一个码，规格定 1 分钟
     * @param resetMaxAttempts  一个验证码最多猜错几次。六位数字若能无限猜，10 分钟足够轮询完
     * @param logResetCodes     把验证码打到日志。V1 没有邮件与短信通道，mysql profile 必须关
     */
    public record Auth(Duration accessTokenTtl, Duration refreshTokenTtl, int maxLoginFailures, Duration lockDuration,
                       Duration resetCodeTtl, Duration resetCodeCooldown, int resetMaxAttempts,
                       boolean logResetCodes) {
    }

    /**
     * @param seedDemoUser 开发环境自动建一个可登录的演示账号，mysql profile 关闭
     */
    public record Dev(boolean seedDemoUser) {
    }

    /**
     * R21–R23 资料下载。
     *
     * @param storageDir    文件落盘目录。V1 没有对象存储，先写本地磁盘
     * @param maxSizeBytes  单个文件上限，要和 material_file 的 CHECK 约束对齐
     * @param signingSecret 签名密钥。留空则启动时随机生成并打 WARN，多实例部署必须显式配置
     */
    public record Material(String storageDir, Duration downloadTtl, Duration uploadTtl,
                           long maxSizeBytes, String signingSecret) {
    }
}
