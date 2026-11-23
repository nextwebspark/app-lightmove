-- The eval judge (#1322): a rubric per dataset, the judge's pick on each pair of answers a run compares, and the
-- pairs a person labels by hand so the judge's agreement with them can be measured. Platform tables: no workspace,
-- and no reference into a tenant table. Every answer here is to a fictional case.

CREATE TABLE app_lm_eval_rubric (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id uuid        NOT NULL REFERENCES app_lm_eval_dataset (id),
    version    integer     NOT NULL CHECK (version >= 1),
    -- [{"key", "name", "better"}], in the order they are judged and listed.
    criteria   jsonb       NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_eval_rubric_version_uk UNIQUE (dataset_id, version)
);

-- A run and a person's labels name the rubric they judged by: a changed rubric is a new version, never a rewrite.
CREATE FUNCTION app_lm_eval_rubric_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'app_lm_eval_rubric is append-only (attempted %)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER app_lm_eval_rubric_append_only
    BEFORE UPDATE OR DELETE ON app_lm_eval_rubric
    FOR EACH ROW EXECUTE FUNCTION app_lm_eval_rubric_is_append_only();

-- The rubric a run's pairs are judged by, fixed when it starts; null for a dataset with none. The judge's version
-- and model are set when the run is first worked.
ALTER TABLE app_lm_eval_run
    ADD COLUMN rubric_id     uuid REFERENCES app_lm_eval_rubric (id),
    ADD COLUMN judge_version integer,
    ADD COLUMN judge_model   varchar(80);

CREATE TABLE app_lm_eval_judgement (
    id                 bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id             uuid        NOT NULL REFERENCES app_lm_eval_run (id),
    case_id            uuid        NOT NULL REFERENCES app_lm_eval_case (id),
    repeat             integer     NOT NULL CHECK (repeat >= 0),
    criterion_key      varchar(40) NOT NULL,
    -- The order the judge saw the pair in, drawn at random for each pair.
    candidate_shown_as char(1)     NOT NULL CHECK (candidate_shown_as IN ('A', 'B')),
    -- Null: the judge gave no pick that could be read for this criterion. Counted for neither side.
    winner             varchar(10) CHECK (winner IN ('CANDIDATE', 'BASELINE', 'TIE')),
    reason             text,
    created_at         timestamptz NOT NULL DEFAULT now(),
    -- One pick per pair and criterion, so a run picked up again never judges twice.
    CONSTRAINT app_lm_eval_judgement_once UNIQUE (run_id, case_id, repeat, criterion_key)
);

CREATE FUNCTION app_lm_eval_judgement_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'app_lm_eval_judgement is append-only (attempted %)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER app_lm_eval_judgement_append_only
    BEFORE UPDATE OR DELETE ON app_lm_eval_judgement
    FOR EACH ROW EXECUTE FUNCTION app_lm_eval_judgement_is_append_only();

-- The pairs a person labels for one rubric version: two versions' answers to the same dev case in a finished run.
-- Which answer is A is drawn at random, so neither the person nor the judge can tell the versions apart.
CREATE TABLE app_lm_eval_calibration_pair (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    rubric_id   uuid        NOT NULL REFERENCES app_lm_eval_rubric (id),
    position    integer     NOT NULL CHECK (position >= 1),
    case_id     uuid        NOT NULL REFERENCES app_lm_eval_case (id),
    a_result_id bigint      NOT NULL REFERENCES app_lm_eval_result (id),
    b_result_id bigint      NOT NULL REFERENCES app_lm_eval_result (id),
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_eval_calibration_pair_position_uk UNIQUE (rubric_id, position)
);

-- A person's pick on one pair and criterion. It may be changed; the judge is then checked again.
CREATE TABLE app_lm_eval_calibration_label (
    pair_id       uuid        NOT NULL REFERENCES app_lm_eval_calibration_pair (id),
    criterion_key varchar(40) NOT NULL,
    pick          varchar(3)  NOT NULL CHECK (pick IN ('A', 'B', 'TIE')),
    labelled_by   uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    labelled_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (pair_id, criterion_key)
);

-- One check of the judge against a rubric's labels: the judge makes its own picks on the same pairs.
CREATE TABLE app_lm_eval_calibration_check (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    rubric_id     uuid        NOT NULL REFERENCES app_lm_eval_rubric (id),
    -- The judge's version that made the picks: a later live version of the judge has not been checked.
    judge_version integer     NOT NULL,
    judge_model   varchar(80),
    status        varchar(12) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'FINISHED', 'FAILED')),
    -- Picks that matched the person's, of the picks compared, when the check finished.
    agreed        integer,
    picks         integer,
    calls_made    integer     NOT NULL DEFAULT 0,
    input_tokens  bigint      NOT NULL DEFAULT 0,
    output_tokens bigint      NOT NULL DEFAULT 0,
    claim_token   uuid,
    claims        integer     NOT NULL DEFAULT 0,
    heartbeat_at  timestamptz,
    started_by    uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    finished_at   timestamptz
);

-- One check of a rubric at a time.
CREATE UNIQUE INDEX app_lm_eval_calibration_check_one_at_a_time
    ON app_lm_eval_calibration_check (rubric_id) WHERE status IN ('QUEUED', 'RUNNING');

CREATE INDEX app_lm_eval_calibration_check_latest_idx
    ON app_lm_eval_calibration_check (rubric_id, created_at DESC);

CREATE TABLE app_lm_eval_calibration_pick (
    check_id      uuid        NOT NULL REFERENCES app_lm_eval_calibration_check (id),
    pair_id       uuid        NOT NULL REFERENCES app_lm_eval_calibration_pair (id),
    criterion_key varchar(40) NOT NULL,
    -- The order the judge saw the pair in: the pair's A first, or its B.
    a_shown_first boolean     NOT NULL,
    -- In the pair's own A and B, whatever order the judge saw. Null: no pick that could be read.
    pick          varchar(3)  CHECK (pick IN ('A', 'B', 'TIE')),
    reason        text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (check_id, pair_id, criterion_key)
);
