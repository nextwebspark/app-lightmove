-- Eval runs (#1320): a candidate prompt version and a baseline answering the same synthetic cases, and what each
-- answered. Platform tables: no workspace, and no reference into a tenant table. A result holds a fictional case's
-- answer only.

CREATE TABLE app_lm_eval_run (
    id                uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    -- What a person calls it: R-0042.
    number            bigint       GENERATED ALWAYS AS IDENTITY UNIQUE,
    prompt_id         varchar(80)  NOT NULL,
    dataset_id        uuid         NOT NULL REFERENCES app_lm_eval_dataset (id),
    split             varchar(10)  NOT NULL CHECK (split IN ('DEV', 'HOLDOUT')),
    -- The cases in use when the run started. One imported or retired since is neither added nor dropped.
    case_ids          uuid[]       NOT NULL,
    -- Versions of app_lm_platform_prompt_version, by number: harden.sql gives that table to postgres, which a
    -- foreign key from here could not then reference. A version is never removed, so the numbers stay true.
    candidate_version integer      NOT NULL,
    baseline_version  integer      NOT NULL,
    -- The model that answered for each side, once it has.
    candidate_model   varchar(80),
    baseline_model    varchar(80),
    repeats           integer      NOT NULL CHECK (repeats BETWEEN 1 AND 5),
    status            varchar(12)  NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'FINISHED', 'CANCELLED', 'FAILED')),
    verdict           varchar(8)   CHECK (verdict IN ('PASS', 'FAIL')),
    failure           varchar(24)  CHECK (failure IN ('CALL_CAP_REACHED', 'MODEL_UNAVAILABLE', 'STOPPED_UNEXPECTEDLY')),
    -- Whether the prompt's cases were reviewed well enough, when the run started, for it to count at put live.
    gate_ready        boolean      NOT NULL,
    case_count        integer      NOT NULL,
    calls_planned     integer      NOT NULL,
    -- Model calls made, a repair's second ask among them; the run stops before the call that would pass call_cap.
    calls_made        integer      NOT NULL DEFAULT 0,
    call_cap          integer      NOT NULL,
    input_tokens      bigint       NOT NULL DEFAULT 0,
    output_tokens     bigint       NOT NULL DEFAULT 0,
    summary           jsonb,
    stop_requested_at timestamptz,
    -- The instance working the run: a claim taken over by another makes the first one's next write find no row.
    claim_token       uuid,
    claims            integer      NOT NULL DEFAULT 0,
    -- Raised before every call: a running run whose worker died is one that has gone quiet.
    heartbeat_at      timestamptz,
    started_by        uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    started_at        timestamptz,
    finished_at       timestamptz,
    CONSTRAINT app_lm_eval_run_verdict_when_finished CHECK (verdict IS NULL OR status = 'FINISHED'),
    CONSTRAINT app_lm_eval_run_failure_when_failed CHECK (failure IS NULL OR status = 'FAILED')
);

-- One run at a time, platform-wide: a second start is refused rather than queued behind the first.
CREATE UNIQUE INDEX app_lm_eval_run_one_at_a_time
    ON app_lm_eval_run ((true)) WHERE status IN ('QUEUED', 'RUNNING');

CREATE INDEX app_lm_eval_run_gate_idx
    ON app_lm_eval_run (prompt_id, baseline_version, candidate_version, finished_at DESC)
    WHERE status = 'FINISHED' AND split = 'HOLDOUT';

CREATE TABLE app_lm_eval_result (
    id            bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id        uuid         NOT NULL REFERENCES app_lm_eval_run (id),
    case_id       uuid         NOT NULL REFERENCES app_lm_eval_case (id),
    side          varchar(10)  NOT NULL CHECK (side IN ('CANDIDATE', 'BASELINE')),
    repeat        integer      NOT NULL CHECK (repeat >= 0),
    -- The answer as the feature bound it; null when the model answered nothing it could read.
    answer        jsonb,
    raw_answer    text,
    finish        varchar(16),
    input_tokens  bigint       NOT NULL DEFAULT 0,
    output_tokens bigint       NOT NULL DEFAULT 0,
    took_millis   bigint       NOT NULL DEFAULT 0,
    -- Each check's verdict on this answer, by check id.
    verdicts      jsonb        NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    -- One answer per case, side and repeat, so a run picked up again never asks twice.
    CONSTRAINT app_lm_eval_result_once UNIQUE (run_id, case_id, side, repeat)
);

-- What a version answered is what a put live rested on: never rewritten, never removed.
CREATE FUNCTION app_lm_eval_result_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'app_lm_eval_result is append-only (attempted %)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER app_lm_eval_result_append_only
    BEFORE UPDATE OR DELETE ON app_lm_eval_result
    FOR EACH ROW EXECUTE FUNCTION app_lm_eval_result_is_append_only();
