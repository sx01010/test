-- R20 找回密码。一次性口令，不复用 auth_token：那张表 token_hash 上有唯一索引，
-- 而两个用户的六位验证码撞号是完全正常的事，撞上唯一索引会让第二个人拿不到码。

CREATE TABLE password_reset_code (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    channel        VARCHAR(8)   NOT NULL,
    code_hash      VARCHAR(128) NOT NULL,
    expires_at     DATETIME     NOT NULL,
    attempt_count  INT          NOT NULL DEFAULT 0,
    consumed_at    DATETIME     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_reset_code_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT ck_reset_code_channel CHECK (channel IN ('EMAIL', 'PHONE'))
);
-- 按用户找最近一条：签发前查冷却，校验时取当前有效码
CREATE INDEX idx_reset_code_user ON password_reset_code (user_id, created_at);
