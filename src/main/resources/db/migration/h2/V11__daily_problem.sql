-- 同 mysql/V11。

CREATE TABLE daily_problem (
    for_date    DATE      NOT NULL,
    problem_id  BIGINT    NOT NULL,
    created_at  DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (for_date),
    CONSTRAINT fk_daily_problem FOREIGN KEY (problem_id) REFERENCES problem (id)
);
CREATE INDEX idx_daily_problem ON daily_problem (problem_id);
