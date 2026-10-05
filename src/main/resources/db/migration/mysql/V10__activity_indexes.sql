-- 运营看板按日期扫全站作答（日活、留存），每日一题的连续打卡按单个用户扫日期。
-- 现有 idx_sub_user_problem 的第二列是 problem_id，按时间范围查用不上。

CREATE INDEX `idx_sub_created` ON `submission` (`created_at`, `user_id`);
CREATE INDEX `idx_sub_user_created` ON `submission` (`user_id`, `created_at`);
CREATE INDEX `idx_user_created` ON `user` (`created_at`);
