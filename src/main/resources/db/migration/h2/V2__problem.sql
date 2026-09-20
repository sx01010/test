CREATE TABLE tag (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    parent_id   BIGINT      NULL,
    name        VARCHAR(64) NOT NULL,
    slug        VARCHAR(64) NOT NULL,
    sort_order  INT         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tag_slug UNIQUE (slug),
    CONSTRAINT fk_tag_parent FOREIGN KEY (parent_id) REFERENCES tag (id)
);
CREATE INDEX idx_tag_parent ON tag (parent_id);

CREATE TABLE problem (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    title               VARCHAR(200) NOT NULL,
    problem_type        VARCHAR(16)  NOT NULL,
    difficulty          TINYINT      NOT NULL,
    grade               VARCHAR(16)  NOT NULL,
    current_version_id  BIGINT       NULL,
    status              VARCHAR(16)  NOT NULL,
    created_by          BIGINT       NOT NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          DATETIME     NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_problem_created_by FOREIGN KEY (created_by) REFERENCES `user` (id),
    CONSTRAINT ck_problem_type CHECK (problem_type IN ('SINGLE', 'MULTI', 'JUDGE', 'NUMERIC', 'BLANK')),
    CONSTRAINT ck_problem_difficulty CHECK (difficulty IN (2, 3, 4)),
    CONSTRAINT ck_problem_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN'))
);
CREATE INDEX idx_problem_list ON problem (status, difficulty, created_at);
CREATE INDEX idx_problem_type ON problem (problem_type);

CREATE TABLE problem_version (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    problem_id          BIGINT        NOT NULL,
    version_no          INT           NOT NULL,
    stem_md             CLOB          NOT NULL,
    options_json        CLOB          NULL,
    answer_json         CLOB          NOT NULL,
    explanation_md      CLOB          NOT NULL,
    grader_config_json  CLOB          NULL,
    max_score           INT           NOT NULL DEFAULT 100,
    change_note         VARCHAR(256)  NULL,
    created_by          BIGINT        NOT NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_problem_version UNIQUE (problem_id, version_no),
    CONSTRAINT fk_problem_version_problem FOREIGN KEY (problem_id) REFERENCES problem (id),
    CONSTRAINT fk_problem_version_created_by FOREIGN KEY (created_by) REFERENCES `user` (id)
);

CREATE TABLE problem_tag (
    problem_id  BIGINT NOT NULL,
    tag_id      BIGINT NOT NULL,
    PRIMARY KEY (problem_id, tag_id),
    CONSTRAINT fk_problem_tag_problem FOREIGN KEY (problem_id) REFERENCES problem (id),
    CONSTRAINT fk_problem_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id)
);
CREATE INDEX idx_tag_problem ON problem_tag (tag_id, problem_id);

CREATE TABLE problem_source (
    problem_id    BIGINT        NOT NULL,
    origin_type   VARCHAR(16)   NOT NULL,
    contest_name  VARCHAR(128)  NULL,
    year          SMALLINT      NULL,
    round         VARCHAR(32)   NULL,
    rewrite_note  VARCHAR(500)  NULL,
    license_ref   VARCHAR(256)  NULL,
    source_url    VARCHAR(512)  NULL,
    PRIMARY KEY (problem_id),
    CONSTRAINT fk_problem_source_problem FOREIGN KEY (problem_id) REFERENCES problem (id),
    CONSTRAINT ck_problem_source_origin CHECK (origin_type IN ('ORIGINAL', 'ADAPTED', 'LICENSED', 'PUBLIC'))
);
CREATE INDEX idx_source_origin ON problem_source (origin_type);
CREATE INDEX idx_source_year ON problem_source (year);
