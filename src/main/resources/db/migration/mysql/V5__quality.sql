-- V1 quality：题目纠错工单与只追加的审计日志。

CREATE TABLE `problem_feedback` (
    `id`                  BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`             BIGINT        NOT NULL COMMENT '提交人',
    `problem_id`          BIGINT        NOT NULL,
    `problem_version_id`  BIGINT        NOT NULL COMMENT '针对哪一版',
    `reason`              VARCHAR(24)   NOT NULL COMMENT 'ANSWER_ERROR / TYPO / UNCLEAR / OTHER',
    `detail`              VARCHAR(500)  NULL COMMENT '补充说明',
    `status`              VARCHAR(16)   NOT NULL COMMENT 'OPEN / FIXED / REJECTED',
    `handled_by`          BIGINT        NULL,
    `handled_at`          DATETIME      NULL,
    `remark`              VARCHAR(256)  NULL COMMENT '处理说明',
    `created_at`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `open_user_id`        BIGINT        GENERATED ALWAYS AS (IF(`status` = 'OPEN', `user_id`, NULL)) STORED,
    `open_problem_id`     BIGINT        GENERATED ALWAYS AS (IF(`status` = 'OPEN', `problem_id`, NULL)) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_feedback_open` (`open_user_id`, `open_problem_id`),
    KEY `idx_feedback_open` (`status`, `created_at`),
    KEY `idx_feedback_problem` (`problem_id`, `status`),
    CONSTRAINT `fk_feedback_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_feedback_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`),
    CONSTRAINT `fk_feedback_version` FOREIGN KEY (`problem_version_id`) REFERENCES `problem_version` (`id`),
    CONSTRAINT `fk_feedback_handled_by` FOREIGN KEY (`handled_by`) REFERENCES `user` (`id`),
    CONSTRAINT `ck_feedback_reason` CHECK (`reason` IN ('ANSWER_ERROR', 'TYPO', 'UNCLEAR', 'OTHER')),
    CONSTRAINT `ck_feedback_status` CHECK (`status` IN ('OPEN', 'FIXED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目纠错工单，同一人同题仅一条 OPEN';

CREATE TABLE `audit_log` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `actor_id`     BIGINT       NULL COMMENT '操作者，系统可空',
    `action`       VARCHAR(48)  NOT NULL COMMENT 'PROBLEM_PUBLISH / PROBLEM_FIX / SUBMISSION_REGRADE / MATERIAL_PUBLISH / MATERIAL_HIDE',
    `target_type`  VARCHAR(16)  NOT NULL COMMENT 'PROBLEM / SUBMISSION / MATERIAL / FEEDBACK',
    `target_id`    BIGINT       NOT NULL,
    `detail_json`  JSON         NULL COMMENT '变更前后与影响行数',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_audit_target` (`target_type`, `target_id`, `created_at`),
    KEY `idx_audit_actor` (`actor_id`, `created_at`),
    CONSTRAINT `fk_audit_actor` FOREIGN KEY (`actor_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='只追加审计，重点记录改题与重判';
