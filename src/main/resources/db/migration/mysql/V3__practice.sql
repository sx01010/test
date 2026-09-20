-- V1 practice：作答快照、错题本、按知识点聚合的进度。

CREATE TABLE `submission` (
    `id`                  BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`             BIGINT        NOT NULL COMMENT '作答人',
    `problem_id`          BIGINT        NOT NULL COMMENT '题目',
    `problem_version_id`  BIGINT        NOT NULL COMMENT '作答时的版本',
    `answer_json`         JSON          NOT NULL COMMENT '用户答案快照',
    `result`              VARCHAR(16)   NOT NULL COMMENT 'CORRECT / PARTIAL / WRONG',
    `score`               DECIMAL(6,2)  NOT NULL COMMENT '得分',
    `max_score`           DECIMAL(6,2)  NOT NULL COMMENT '满分',
    `details_json`        JSON          NULL COMMENT '逐空对错',
    `duration_ms`         INT           NULL COMMENT '不计时就留空，不参与判分',
    `idempotency_key`     VARCHAR(64)   NOT NULL COMMENT '前端 UUID，重试复用、重新作答换新值',
    `regraded_from`       BIGINT        NULL COMMENT '非空表示由纠错重判产生',
    `created_at`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_submission_idemp` (`user_id`, `idempotency_key`),
    KEY `idx_sub_user_problem` (`user_id`, `problem_id`, `created_at`),
    KEY `idx_sub_version` (`problem_version_id`),
    CONSTRAINT `fk_submission_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_submission_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`),
    CONSTRAINT `fk_submission_version` FOREIGN KEY (`problem_version_id`) REFERENCES `problem_version` (`id`),
    CONSTRAINT `fk_submission_regraded_from` FOREIGN KEY (`regraded_from`) REFERENCES `submission` (`id`),
    CONSTRAINT `ck_submission_result` CHECK (`result` IN ('CORRECT', 'PARTIAL', 'WRONG'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='一次作答快照，重判写新行';

CREATE TABLE `wrong_item` (
    `id`                    BIGINT    NOT NULL AUTO_INCREMENT,
    `user_id`               BIGINT    NOT NULL,
    `problem_id`            BIGINT    NOT NULL,
    `last_submission_id`    BIGINT    NOT NULL COMMENT '最近一次相关提交',
    `last_version_id`       BIGINT    NOT NULL COMMENT '做错时的版本，用于提示题目已更新',
    `wrong_count`           INT       NOT NULL COMMENT '累计做错',
    `consecutive_correct`   INT       NOT NULL DEFAULT 0 COMMENT '连续做对次数，达阈值自动掌握',
    `mastered`              TINYINT   NOT NULL DEFAULT 0 COMMENT '1 表示已掌握',
    `last_wrong_at`         DATETIME  NOT NULL COMMENT '最近做错时间，供时间筛选',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wrong` (`user_id`, `problem_id`),
    KEY `idx_wrong_user` (`user_id`, `mastered`, `last_wrong_at`),
    CONSTRAINT `fk_wrong_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_wrong_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`),
    CONSTRAINT `fk_wrong_submission` FOREIGN KEY (`last_submission_id`) REFERENCES `submission` (`id`),
    CONSTRAINT `fk_wrong_version` FOREIGN KEY (`last_version_id`) REFERENCES `problem_version` (`id`),
    CONSTRAINT `ck_wrong_mastered` CHECK (`mastered` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错题本，一人一题一行';

CREATE TABLE `user_tag_progress` (
    `user_id`            BIGINT    NOT NULL,
    `tag_id`             BIGINT    NOT NULL,
    `attempt_count`      INT       NOT NULL COMMENT '作答次数',
    `correct_count`      INT       NOT NULL COMMENT '全对次数',
    `last_submitted_at`  DATETIME  NULL COMMENT '最近作答',
    PRIMARY KEY (`user_id`, `tag_id`),
    CONSTRAINT `fk_progress_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_progress_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='按知识点聚合的进度';
