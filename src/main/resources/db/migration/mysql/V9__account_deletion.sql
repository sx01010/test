-- 账号注销走匿名化而不是物理删除：提交记录、纠错工单都外键指向 user，
-- 物理删除要么级联掉统计数据，要么留一堆悬空外键。
-- 注销后邮箱和手机号置空、状态改 DELETED，所以「至少一个联系方式」只对未注销账号成立。

ALTER TABLE `user` DROP CHECK `ck_user_contact`;
ALTER TABLE `user` DROP CHECK `ck_user_status`;
ALTER TABLE `user` ADD COLUMN `deleted_at` DATETIME NULL COMMENT '注销时间' AFTER `last_login_at`;
ALTER TABLE `user` ADD CONSTRAINT `ck_user_contact`
    CHECK (`email` IS NOT NULL OR `phone` IS NOT NULL OR `status` = 'DELETED');
ALTER TABLE `user` ADD CONSTRAINT `ck_user_status`
    CHECK (`status` IN ('ACTIVE', 'LOCKED', 'DELETED'));
