-- 同 mysql/V10。

CREATE INDEX idx_sub_created ON submission (created_at, user_id);
CREATE INDEX idx_sub_user_created ON submission (user_id, created_at);
CREATE INDEX idx_user_created ON `user` (created_at);
