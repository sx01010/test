-- H2 版本用于本地开发与测试，结构与 mysql/ 下同名脚本保持一致。
-- 差异只在 H2 不支持的特性：JSON 列改 CLOB、去掉 ON UPDATE、去掉 ngram 全文索引与生成列。

CREATE TABLE `user` (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    nickname        VARCHAR(32)  NOT NULL,
    email           VARCHAR(128) NULL,
    phone           VARCHAR(20)  NULL,
    password_hash   VARCHAR(128) NOT NULL,
    avatar_preset   TINYINT      NOT NULL DEFAULT 0,
    role            VARCHAR(16)  NOT NULL,
    practice_mode   TINYINT      NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL,
    fail_count      INT          NOT NULL DEFAULT 0,
    locked_until    DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at   DATETIME     NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT uk_user_phone UNIQUE (phone),
    CONSTRAINT ck_user_contact CHECK (email IS NOT NULL OR phone IS NOT NULL),
    CONSTRAINT ck_user_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE', 'LOCKED')),
    CONSTRAINT ck_user_practice_mode CHECK (practice_mode IN (0, 1))
);
