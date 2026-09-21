-- R20 找回密码。一次性口令，不复用 auth_token：那张表 token_hash 上有唯一索引，
-- 而两个用户的六位验证码撞号是完全正常的事，撞上唯一索引会让第二个人拿不到码。

CREATE TABLE `password_reset_code` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT       NOT NULL,
    `channel`        VARCHAR(8)   NOT NULL COMMENT 'EMAIL / PHONE',
    `code_hash`      VARCHAR(128) NOT NULL COMMENT 'BCrypt，不存明文',
    `expires_at`     DATETIME     NOT NULL COMMENT '默认签发后 10 分钟',
    `attempt_count`  INT          NOT NULL DEFAULT 0 COMMENT '猜错次数，超上限即作废',
    `consumed_at`    DATETIME     NULL COMMENT '用掉的时间，一次性',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_reset_code_user` (`user_id`, `created_at`),
    CONSTRAINT `fk_reset_code_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `ck_reset_code_channel` CHECK (`channel` IN ('EMAIL', 'PHONE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='找回密码的一次性验证码';
