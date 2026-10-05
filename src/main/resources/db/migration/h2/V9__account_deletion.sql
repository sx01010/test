-- 账号注销走匿名化。结构与 mysql/V9 一致。

ALTER TABLE `user` DROP CONSTRAINT ck_user_contact;
ALTER TABLE `user` DROP CONSTRAINT ck_user_status;
ALTER TABLE `user` ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE `user` ADD CONSTRAINT ck_user_contact
    CHECK (email IS NOT NULL OR phone IS NOT NULL OR status = 'DELETED');
ALTER TABLE `user` ADD CONSTRAINT ck_user_status
    CHECK (status IN ('ACTIVE', 'LOCKED', 'DELETED'));
