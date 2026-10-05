-- 每日一题。当天第一次被请求时选定并落库：只按日期取模的话，白天新发布一道题，
-- 当天的题就会换掉，上午做完的人下午看到的是另一道。

CREATE TABLE `daily_problem` (
    `for_date`    DATE      NOT NULL COMMENT '北京时间日期',
    `problem_id`  BIGINT    NOT NULL,
    `created_at`  DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`for_date`),
    KEY `idx_daily_problem` (`problem_id`),
    CONSTRAINT `fk_daily_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日一题';
