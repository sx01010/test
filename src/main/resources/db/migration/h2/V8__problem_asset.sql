-- 题干与解析的配图。结构与 mysql/V8 一致。

CREATE TABLE problem_asset (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    object_key  VARCHAR(64)  NOT NULL,
    mime_type   VARCHAR(32)  NOT NULL,
    size_bytes  INT          NOT NULL,
    sha256      CHAR(64)     NOT NULL,
    created_by  BIGINT       NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_problem_asset_key UNIQUE (object_key),
    CONSTRAINT fk_problem_asset_created_by FOREIGN KEY (created_by) REFERENCES `user` (id),
    CONSTRAINT ck_problem_asset_mime CHECK (mime_type IN ('image/png', 'image/jpeg', 'image/webp', 'image/svg+xml')),
    CONSTRAINT ck_problem_asset_size CHECK (size_bytes > 0 AND size_bytes <= 2097152)
);
CREATE INDEX idx_problem_asset_sha ON problem_asset (sha256);
