-- V1 identity：账号。只存昵称与一个联系方式，不存真实姓名、学校、生日。
-- 表名 user 是 MySQL 保留字，全程使用反引号。

CREATE TABLE `user` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `nickname`        VARCHAR(32)  NOT NULL COMMENT '展示名',
    `email`           VARCHAR(128) NULL COMMENT '与 phone 至少填一个',
    `phone`           VARCHAR(20)  NULL COMMENT '与 email 至少填一个',
    `password_hash`   VARCHAR(128) NOT NULL COMMENT 'BCrypt',
    `avatar_preset`   TINYINT      NOT NULL DEFAULT 0 COMMENT '预设头像编号，不做上传',
    `role`            VARCHAR(16)  NOT NULL COMMENT 'USER / ADMIN',
    `practice_mode`   TINYINT      NOT NULL DEFAULT 0 COMMENT '1 表示做完才看解析',
    `status`          VARCHAR(16)  NOT NULL COMMENT 'ACTIVE / LOCKED',
    `fail_count`      INT          NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    `locked_until`    DATETIME     NULL COMMENT '锁定截止时间',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `last_login_at`   DATETIME     NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_email` (`email`),
    UNIQUE KEY `uk_user_phone` (`phone`),
    CONSTRAINT `ck_user_contact` CHECK (`email` IS NOT NULL OR `phone` IS NOT NULL),
    CONSTRAINT `ck_user_role` CHECK (`role` IN ('USER', 'ADMIN')),
    CONSTRAINT `ck_user_status` CHECK (`status` IN ('ACTIVE', 'LOCKED')),
    CONSTRAINT `ck_user_practice_mode` CHECK (`practice_mode` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号';
