-- V1 规格的 13 张表里没有令牌表，但 R02 要求「refresh token 轮换，旧 token 立即作废」，
-- 作废必须有服务端状态。这里补一张只存哈希的令牌表，明文令牌不落库。

CREATE TABLE `auth_token` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL,
    `token_hash`  CHAR(64)     NOT NULL COMMENT '明文令牌的 SHA-256 十六进制',
    `token_type`  VARCHAR(16)  NOT NULL COMMENT 'ACCESS / REFRESH',
    `expires_at`  DATETIME     NOT NULL,
    `revoked_at`  DATETIME     NULL COMMENT '轮换或登出后置位',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_auth_token_hash` (`token_hash`),
    KEY `idx_auth_token_user` (`user_id`, `token_type`, `expires_at`),
    CONSTRAINT `fk_auth_token_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `ck_auth_token_type` CHECK (`token_type` IN ('ACCESS', 'REFRESH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不透明令牌，只存哈希';
