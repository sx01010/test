CREATE TABLE material (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    title         VARCHAR(200)  NOT NULL,
    material_type VARCHAR(16)   NOT NULL,
    grade         VARCHAR(16)   NULL,
    year          SMALLINT      NULL,
    origin_type   VARCHAR(16)   NOT NULL,
    license_ref   VARCHAR(256)  NULL,
    source_url    VARCHAR(512)  NULL,
    status        VARCHAR(16)   NOT NULL,
    created_by    BIGINT        NOT NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_material_created_by FOREIGN KEY (created_by) REFERENCES `user` (id),
    CONSTRAINT ck_material_type CHECK (material_type IN ('PAPER', 'HANDOUT')),
    CONSTRAINT ck_material_origin CHECK (origin_type IN ('OWNED', 'LICENSED', 'PUBLIC')),
    CONSTRAINT ck_material_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN'))
);
CREATE INDEX idx_material_list ON material (status, material_type, grade, year);

CREATE TABLE material_file (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    material_id    BIGINT        NOT NULL,
    object_key     VARCHAR(512)  NOT NULL,
    original_name  VARCHAR(255)  NOT NULL,
    mime_type      VARCHAR(64)   NOT NULL,
    size_bytes     BIGINT        NOT NULL,
    sha256         CHAR(64)      NOT NULL,
    scan_status    VARCHAR(16)   NOT NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at     DATETIME      NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_material_file_object_key UNIQUE (object_key),
    CONSTRAINT fk_material_file_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT ck_material_file_mime CHECK (mime_type = 'application/pdf'),
    CONSTRAINT ck_material_file_size CHECK (size_bytes > 0 AND size_bytes <= 31457280),
    CONSTRAINT ck_material_file_scan CHECK (scan_status IN ('PENDING', 'CLEAN', 'REJECTED'))
);
CREATE INDEX idx_material_file_material ON material_file (material_id);
