CREATE TABLE submission (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    user_id             BIGINT        NOT NULL,
    problem_id          BIGINT        NOT NULL,
    problem_version_id  BIGINT        NOT NULL,
    answer_json         CLOB          NOT NULL,
    result              VARCHAR(16)   NOT NULL,
    score               DECIMAL(6,2)  NOT NULL,
    max_score           DECIMAL(6,2)  NOT NULL,
    details_json        CLOB          NULL,
    duration_ms         INT           NULL,
    idempotency_key     VARCHAR(64)   NOT NULL,
    regraded_from       BIGINT        NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_submission_idemp UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_submission_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_submission_problem FOREIGN KEY (problem_id) REFERENCES problem (id),
    CONSTRAINT fk_submission_version FOREIGN KEY (problem_version_id) REFERENCES problem_version (id),
    CONSTRAINT fk_submission_regraded_from FOREIGN KEY (regraded_from) REFERENCES submission (id),
    CONSTRAINT ck_submission_result CHECK (result IN ('CORRECT', 'PARTIAL', 'WRONG'))
);
CREATE INDEX idx_sub_user_problem ON submission (user_id, problem_id, created_at);
CREATE INDEX idx_sub_version ON submission (problem_version_id);

CREATE TABLE wrong_item (
    id                    BIGINT    NOT NULL AUTO_INCREMENT,
    user_id               BIGINT    NOT NULL,
    problem_id            BIGINT    NOT NULL,
    last_submission_id    BIGINT    NOT NULL,
    last_version_id       BIGINT    NOT NULL,
    wrong_count           INT       NOT NULL,
    consecutive_correct   INT       NOT NULL DEFAULT 0,
    mastered              TINYINT   NOT NULL DEFAULT 0,
    last_wrong_at         DATETIME  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_wrong UNIQUE (user_id, problem_id),
    CONSTRAINT fk_wrong_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_wrong_problem FOREIGN KEY (problem_id) REFERENCES problem (id),
    CONSTRAINT fk_wrong_submission FOREIGN KEY (last_submission_id) REFERENCES submission (id),
    CONSTRAINT fk_wrong_version FOREIGN KEY (last_version_id) REFERENCES problem_version (id),
    CONSTRAINT ck_wrong_mastered CHECK (mastered IN (0, 1))
);
CREATE INDEX idx_wrong_user ON wrong_item (user_id, mastered, last_wrong_at);

CREATE TABLE user_tag_progress (
    user_id            BIGINT    NOT NULL,
    tag_id             BIGINT    NOT NULL,
    attempt_count      INT       NOT NULL,
    correct_count      INT       NOT NULL,
    last_submitted_at  DATETIME  NULL,
    PRIMARY KEY (user_id, tag_id),
    CONSTRAINT fk_progress_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_progress_tag FOREIGN KEY (tag_id) REFERENCES tag (id)
);
