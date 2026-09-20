-- V1 problem：知识点、题目头、不可变版本、多对多标签、来源授权。

CREATE TABLE `tag` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `parent_id`   BIGINT      NULL COMMENT '父知识点',
    `name`        VARCHAR(64) NOT NULL COMMENT '如“行程问题”',
    `slug`        VARCHAR(64) NOT NULL COMMENT '稳定标识',
    `sort_order`  INT         NOT NULL COMMENT '同级排序',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_slug` (`slug`),
    KEY `idx_tag_parent` (`parent_id`),
    CONSTRAINT `fk_tag_parent` FOREIGN KEY (`parent_id`) REFERENCES `tag` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点树';

CREATE TABLE `problem` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `title`               VARCHAR(200) NOT NULL COMMENT '题目标题',
    `problem_type`        VARCHAR(16)  NOT NULL COMMENT 'SINGLE / MULTI / JUDGE / NUMERIC / BLANK',
    `difficulty`          TINYINT      NOT NULL COMMENT '2 入门 / 3 进阶 / 4 挑战',
    `grade`               VARCHAR(16)  NOT NULL COMMENT '适用年级',
    `current_version_id`  BIGINT       NULL COMMENT '当前对外版本，录入后再回填，避免循环外键',
    `status`              VARCHAR(16)  NOT NULL COMMENT 'DRAFT / PUBLISHED / HIDDEN',
    `created_by`          BIGINT       NOT NULL COMMENT '录入管理员',
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at`          DATETIME     NULL COMMENT '软删',
    PRIMARY KEY (`id`),
    KEY `idx_problem_list` (`status`, `difficulty`, `created_at`),
    KEY `idx_problem_type` (`problem_type`),
    CONSTRAINT `fk_problem_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`),
    CONSTRAINT `ck_problem_type` CHECK (`problem_type` IN ('SINGLE', 'MULTI', 'JUDGE', 'NUMERIC', 'BLANK')),
    CONSTRAINT `ck_problem_difficulty` CHECK (`difficulty` IN (2, 3, 4)),
    CONSTRAINT `ck_problem_status` CHECK (`status` IN ('DRAFT', 'PUBLISHED', 'HIDDEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目头信息';

CREATE TABLE `problem_version` (
    `id`                  BIGINT        NOT NULL AUTO_INCREMENT,
    `problem_id`          BIGINT        NOT NULL COMMENT '所属题目',
    `version_no`          INT           NOT NULL COMMENT '从 1 递增',
    `stem_md`             MEDIUMTEXT    NOT NULL COMMENT '题干 Markdown + LaTeX',
    `options_json`        JSON          NULL COMMENT '选项，不含对错标记',
    `answer_json`         JSON          NOT NULL COMMENT '标准答案，任何列表接口都不返回',
    `explanation_md`      MEDIUMTEXT    NOT NULL COMMENT '解析，必填，自己撰写',
    `grader_config_json`  JSON          NULL COMMENT 'tolerance、orderIndependent',
    `max_score`           INT           NOT NULL DEFAULT 100 COMMENT '满分',
    `change_note`         VARCHAR(256)  NULL COMMENT '为什么升版，纠错修正时必填',
    `created_by`          BIGINT        NOT NULL,
    `created_at`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_problem_version` (`problem_id`, `version_no`),
    CONSTRAINT `fk_problem_version_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`),
    CONSTRAINT `fk_problem_version_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变题目快照';

CREATE TABLE `problem_tag` (
    `problem_id`  BIGINT NOT NULL,
    `tag_id`      BIGINT NOT NULL,
    PRIMARY KEY (`problem_id`, `tag_id`),
    KEY `idx_tag_problem` (`tag_id`, `problem_id`),
    CONSTRAINT `fk_problem_tag_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`),
    CONSTRAINT `fk_problem_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目与知识点多对多';

CREATE TABLE `problem_source` (
    `problem_id`    BIGINT        NOT NULL COMMENT '一对一挂题目',
    `origin_type`   VARCHAR(16)   NOT NULL COMMENT 'ORIGINAL / ADAPTED / LICENSED / PUBLIC',
    `contest_name`  VARCHAR(128)  NULL COMMENT '参考赛事名，仅作标注',
    `year`          SMALLINT      NULL COMMENT '年份',
    `round`         VARCHAR(32)   NULL COMMENT '轮次',
    `rewrite_note`  VARCHAR(500)  NULL COMMENT 'ADAPTED 必填：改了哪些数据和表述',
    `license_ref`   VARCHAR(256)  NULL COMMENT 'LICENSED 必填：授权凭证编号或文件 key',
    `source_url`    VARCHAR(512)  NULL COMMENT 'PUBLIC 填公开来源链接',
    PRIMARY KEY (`problem_id`),
    KEY `idx_source_origin` (`origin_type`),
    KEY `idx_source_year` (`year`),
    CONSTRAINT `fk_problem_source_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`),
    CONSTRAINT `ck_problem_source_origin` CHECK (`origin_type` IN ('ORIGINAL', 'ADAPTED', 'LICENSED', 'PUBLIC'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='来源与授权登记，字段不齐不允许发布';

-- 题干与标题关键词搜索依赖 ngram 解析器（MySQL 5.7.6+ / 8.0 默认可用）。
ALTER TABLE `problem` ADD FULLTEXT KEY `ft_problem_title` (`title`) WITH PARSER ngram;
ALTER TABLE `problem_version` ADD FULLTEXT KEY `ft_problem_version_stem` (`stem_md`) WITH PARSER ngram;
