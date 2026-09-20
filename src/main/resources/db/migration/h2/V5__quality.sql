-- MySQL 用生成列 + 唯一索引保证「同一人同题只有一条 OPEN」。
-- H2 不支持那种写法，这里只建普通索引，唯一性由 FeedbackService 在事务里保证。

CREATE TABLE problem_feedback (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    user_id             BIGINT        NOT NULL,
    problem_id          BIGINT        NOT NULL,
    problem_version_id  BIGINT        NOT NULL,
    reason              VARCHAR(24)   NOT NULL,
    detail              VARCHAR(500)  NULL,
    status              VARCHAR(16)   NOT NULL,
    handled_by          BIGINT        NULL,
    handled_at          DATETIME      NULL,
    remark              VARCHAR(256)  NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_feedback_problem FOREIGN KEY (problem_id) REFERENCES problem (id),
    CONSTRAINT fk_feedback_version FOREIGN KEY (problem_version_id) REFERENCES problem_version (id),
    CONSTRAINT fk_feedback_handled_by FOREIGN KEY (handled_by) REFERENCES `user` (id),
    CONSTRAINT ck_feedback_reason CHECK (reason IN ('ANSWER_ERROR', 'TYPO', 'UNCLEAR', 'OTHER')),
    CONSTRAINT ck_feedback_status CHECK (status IN ('OPEN', 'FIXED', 'REJECTED'))
);
CREATE INDEX idx_feedback_open ON problem_feedback (status, created_at);
CREATE INDEX idx_feedback_problem ON problem_feedback (problem_id, status);

CREATE TABLE audit_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id     BIGINT       NULL,
    action       VARCHAR(48)  NOT NULL,
    target_type  VARCHAR(16)  NOT NULL,
    target_id    BIGINT       NOT NULL,
    detail_json  CLOB         NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES `user` (id)
);
CREATE INDEX idx_audit_target ON audit_log (target_type, target_id, created_at);
CREATE INDEX idx_audit_actor ON audit_log (actor_id, created_at);
