-- Fair use (epic #734, #738): search and AI carry no price, so each use is recorded with what it is estimated to
-- have cost us, and a workspace is refused only past a monthly ceiling per seat.
CREATE TABLE app_lm_usage_event (
    id               bigserial     PRIMARY KEY,
    workspace_id     uuid          NOT NULL REFERENCES app_lm_workspace (id),
    user_id          uuid,
    project_id       uuid,
    kind             varchar(32)   NOT NULL
        CONSTRAINT app_lm_usage_event_kind_chk
            CHECK (kind IN ('PEOPLE_SEARCH_PAGE', 'SOURCING_RUN', 'AI_ENRICH', 'OUTREACH_OPENER', 'ASSISTANT_ASK')),
    -- Profiles on a page, executives a run filed, people an opener was written for, or one per enrichment or ask.
    units            integer       NOT NULL
        CONSTRAINT app_lm_usage_event_units_chk CHECK (units >= 0),
    est_cost_fils    bigint        NOT NULL
        CONSTRAINT app_lm_usage_event_cost_chk CHECK (est_cost_fils >= 0),
    -- Set where one use must be counted once: a people page per workspace, a sourcing run.
    idempotency_key  varchar(160),
    created_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_usage_event_key_uk UNIQUE (workspace_id, idempotency_key)
);

COMMENT ON TABLE app_lm_usage_event IS
    'One use of an unpriced action (search, AI) and its estimated vendor cost: the fair-use meter and the margin report.';

CREATE INDEX app_lm_usage_event_workspace_kind_idx ON app_lm_usage_event (workspace_id, kind, created_at);

-- What an assistant answer cost in tokens, summed over the supervisor and the specialists it asked. V70 dropped V65's
-- columns with the background turn; null on a turn answered before this.
ALTER TABLE app_lm_assistant_turn
    ADD COLUMN model         varchar(64),
    ADD COLUMN input_tokens  integer,
    ADD COLUMN output_tokens integer;
