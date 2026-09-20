CREATE TABLE auth_token (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token_hash  CHAR(64)     NOT NULL,
    token_type  VARCHAR(16)  NOT NULL,
    expires_at  DATETIME     NOT NULL,
    revoked_at  DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_token_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT ck_auth_token_type CHECK (token_type IN ('ACCESS', 'REFRESH'))
);
CREATE INDEX idx_auth_token_user ON auth_token (user_id, token_type, expires_at);
