package com.mathematics.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mathematics")
public record AppProperties(Practice practice, Auth auth, Dev dev, Material material) {

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
