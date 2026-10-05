-- 题干与解析的配图。只有管理员能上传；题干里用 ![说明](asset:对象键) 引用。
-- 不挂 problem 外键：同一张图可以被多道题、多个版本引用，版本快照里存的是 Markdown 文本，
-- 删一张图不该去改不可变的 problem_version。

CREATE TABLE `problem_asset` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `object_key`  VARCHAR(64)  NOT NULL COMMENT '随机不可预测，同时是公开地址的一部分',
    `mime_type`   VARCHAR(32)  NOT NULL COMMENT 'image/png / image/jpeg / image/webp / image/svg+xml',
    `size_bytes`  INT          NOT NULL,
    `sha256`      CHAR(64)     NOT NULL,
    `created_by`  BIGINT       NOT NULL,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_problem_asset_key` (`object_key`),
    KEY `idx_problem_asset_sha` (`sha256`),
    CONSTRAINT `fk_problem_asset_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`),
    CONSTRAINT `ck_problem_asset_mime` CHECK (`mime_type` IN ('image/png', 'image/jpeg', 'image/webp', 'image/svg+xml')),
    CONSTRAINT `ck_problem_asset_size` CHECK (`size_bytes` > 0 AND `size_bytes` <= 2097152)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目配图';
