-- V1 material：可下载资料元数据与对象存储引用。应用不代理文件流，只签发短期 URL。

CREATE TABLE `material` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `title`         VARCHAR(200)  NOT NULL COMMENT '资料标题',
    `material_type` VARCHAR(16)   NOT NULL COMMENT 'PAPER / HANDOUT',
    `grade`         VARCHAR(16)   NULL COMMENT '筛选维度',
    `year`          SMALLINT      NULL COMMENT '筛选维度',
    `origin_type`   VARCHAR(16)   NOT NULL COMMENT 'OWNED / LICENSED / PUBLIC',
    `license_ref`   VARCHAR(256)  NULL COMMENT 'LICENSED 必填',
    `source_url`    VARCHAR(512)  NULL COMMENT 'PUBLIC 必填',
    `status`        VARCHAR(16)   NOT NULL COMMENT 'DRAFT / PUBLISHED / HIDDEN',
    `created_by`    BIGINT        NOT NULL,
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_material_list` (`status`, `material_type`, `grade`, `year`),
    CONSTRAINT `fk_material_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`),
    CONSTRAINT `ck_material_type` CHECK (`material_type` IN ('PAPER', 'HANDOUT')),
    CONSTRAINT `ck_material_origin` CHECK (`origin_type` IN ('OWNED', 'LICENSED', 'PUBLIC')),
    CONSTRAINT `ck_material_status` CHECK (`status` IN ('DRAFT', 'PUBLISHED', 'HIDDEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可下载资料元数据与发布闸门';

CREATE TABLE `material_file` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `material_id`    BIGINT        NOT NULL,
    `object_key`     VARCHAR(512)  NOT NULL COMMENT '不可预测的对象键',
    `original_name`  VARCHAR(255)  NOT NULL COMMENT '展示文件名',
    `mime_type`      VARCHAR(64)   NOT NULL COMMENT 'V1 仅 application/pdf',
    `size_bytes`     BIGINT        NOT NULL COMMENT '≤ 30 MB',
    `sha256`         CHAR(64)      NOT NULL COMMENT '校验完整性',
    `scan_status`    VARCHAR(16)   NOT NULL COMMENT 'PENDING / CLEAN / REJECTED',
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted_at`     DATETIME      NULL COMMENT '下架后延迟 7 天清理',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_material_file_object_key` (`object_key`),
    KEY `idx_material_file_material` (`material_id`),
    CONSTRAINT `fk_material_file_material` FOREIGN KEY (`material_id`) REFERENCES `material` (`id`),
    CONSTRAINT `ck_material_file_mime` CHECK (`mime_type` = 'application/pdf'),
    CONSTRAINT `ck_material_file_size` CHECK (`size_bytes` > 0 AND `size_bytes` <= 31457280),
    CONSTRAINT `ck_material_file_scan` CHECK (`scan_status` IN ('PENDING', 'CLEAN', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对象存储文件引用';
