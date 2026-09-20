-- The Uncava Assistant's own rows: a conversation, the turns it is made of, and the append-only log
-- one running turn writes.
--
-- Three tables and not two, because a turn and a message are different lifecycles. A turn can fail,
-- be cancelled, or run for three minutes and produce nothing; a message is what a person reads back.
-- Folding them together would mean either a message row that is sometimes not a message, or a status
-- column on a thing that has no status.
--
-- Deliberately no app_lm_assistant_message: a turn IS the exchange — the question on the way in, the
-- answer on the way out — so the conversation is `SELECT ... FROM app_lm_assistant_turn ORDER BY
-- created_at`, with no join and nothing to keep in step. Tool calls and streamed text are events, not
-- messages.

-- ── The conversation ──────────────────────────────────────────────────────────
--
-- Scoped to (workspace, user) and NOT to a project: the assistant opens from every screen, so a
-- thread belongs to the person who started it. project_id is the mandate it was asked *about*, which
-- is context rather than ownership — a thread started on Strategy still reads back when the user is
-- on the roster.
CREATE TABLE app_lm_assistant_thread (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Tenant isolation is by this column and AuthPrincipal.requireWorkspaceId(), never by a
    -- request parameter. user_id is here too because a thread is one person's, not the firm's:
    -- nobody else in the workspace may read it.
    workspace_id uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    user_id      uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,

    -- ON DELETE SET NULL, for V36's reason one table over: removing a mandate must not delete the
    -- conversations held about it. The thread survives, unmoored, and still reads back.
    project_id   uuid        REFERENCES app_lm_project (id) ON DELETE SET NULL,

    -- Named from its first question when the thread is created, so the history list is legible
    -- without reading into the turns. Not null because a thread is born with its first turn; the
    -- panel's "New chat" is a client-side reset and writes nothing until something is asked.
    title        text        NOT NULL,

    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    version      bigint      NOT NULL DEFAULT 0
);

-- The history list: this user's threads, newest first. updated_at rather than created_at, because a
-- thread returned to is a recent thread.
CREATE INDEX app_lm_assistant_thread_recent_idx
    ON app_lm_assistant_thread (workspace_id, user_id, updated_at DESC);

CREATE TRIGGER app_lm_assistant_thread_touch BEFORE UPDATE ON app_lm_assistant_thread
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- ── One exchange ──────────────────────────────────────────────────────────────
--
-- A turn is accepted on a request thread and then runs in the background, because it can outlive the
-- 55s SSE cycle that ProjectStreamRegistry works within. Everything the worker cannot look up for
-- itself is therefore resolved at accept time and written here.
CREATE TABLE app_lm_assistant_turn (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id      uuid        NOT NULL REFERENCES app_lm_assistant_thread (id) ON DELETE CASCADE,

    status         varchar(16) NOT NULL
        CONSTRAINT app_lm_assistant_turn_status_chk
            CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),

    question       text        NOT NULL,

    -- Null until the turn ends. The streamed deltas are events; this is the settled text, so a
    -- reload renders the conversation without replaying an event log.
    --
    -- Deliberately NOT tied to status by a constraint, unlike finished_at below. A turn must always
    -- be able to reach a terminal state: one that succeeded having only emitted a proposal has no
    -- prose to store, and one that died on an exception nobody mapped has no ErrorCode to name. The
    -- worst failure here is a turn stuck RUNNING forever while the SPA reconnects every 55s, and a
    -- CHECK that can refuse the row recording the end is how you get one.
    answer         text,

    -- The ErrorCode a failed turn ended on, not a message: the SPA switches on `code`, never on
    -- `detail`, and a turn that failed off-request never reached GlobalExceptionHandler.
    error_code     varchar(64),

    -- The actor, carried rather than inferred. A background thread has an empty SecurityContext, so
    -- the guard beans are called directly with an AuthPrincipal rebuilt from these two columns and
    -- they re-read the database exactly as they do on a request. Storing them is not a shortcut past
    -- authorisation; it is what makes authorisation possible off-request.
    --
    -- Not redundant with the thread's own columns: a workspace membership can change mid-turn, and
    -- what a turn was authorised as is a fact about the turn.
    actor_user_id  uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    workspace_id   uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,

    -- Audit context, resolved on the accept thread because the worker has no HttpServletRequest and
    -- Tomcat recycles the real one after the 202. AuditService.Builder.from(HttpServletRequest) is
    -- the only writer of ip/user-agent and CorrelationId.current() answers "none" off-request, so
    -- without these three columns an assistant-driven write would land in the audit trail anonymous.
    ip_address     varchar(45),
    user_agent     varchar(512),
    correlation_id varchar(64),

    -- What actually served the turn, and what it cost. LlmBudgetGuard counts requests, which for one
    -- request driving twenty tool calls is off by an order of magnitude; these are the numbers a
    -- per-workspace spend figure can be built from.
    model          varchar(64),
    input_tokens   integer,
    output_tokens  integer,

    started_at     timestamptz NOT NULL DEFAULT now(),
    finished_at    timestamptz,

    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    version        bigint      NOT NULL DEFAULT 0,

    -- A turn that has stopped says when, and one still running has not. Catches a worker that sets a
    -- terminal status without stamping the clock, which would otherwise only show up as a turn that
    -- looks finished and can never be aged out.
    CONSTRAINT app_lm_assistant_turn_finished_chk
        CHECK ((status = 'RUNNING') = (finished_at IS NULL)),

    -- The one direction that is safe to forbid. Requiring an error_code on FAILED, or an answer on
    -- SUCCEEDED, could refuse the write that ends a turn; refusing an error_code on a turn that
    -- succeeded cannot, because there is no honest reason to set one.
    CONSTRAINT app_lm_assistant_turn_succeeded_chk
        CHECK (status <> 'SUCCEEDED' OR error_code IS NULL)
);

-- The conversation, in order, for one thread.
CREATE INDEX app_lm_assistant_turn_thread_idx
    ON app_lm_assistant_turn (thread_id, created_at);

-- Sweeping turns abandoned by an instance that went away. Partial, because the rows worth finding
-- are the few still RUNNING and never the accumulated history.
CREATE INDEX app_lm_assistant_turn_running_idx
    ON app_lm_assistant_turn (started_at)
    WHERE status = 'RUNNING';

CREATE TRIGGER app_lm_assistant_turn_touch BEFORE UPDATE ON app_lm_assistant_turn
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- ── The append-only log a running turn writes ─────────────────────────────────
--
-- This is what makes a turn resumable. The SPA holds an SSE stream that the server closes every ~55s
-- by design, and Cloud Run has no sticky routing, so a turn running on one instance and a stream held
-- on another must meet somewhere both can reach. They meet here: the NOTIFY that wakes a listener
-- carries only (turn_id, seq) — Postgres caps a payload at 8000 bytes and the project stream sets the
-- precedent of announcing a change rather than shipping it — and the content is read back by cursor.
--
-- Reconnect therefore replays from `seq` rather than losing whatever committed while the socket was
-- down, and a refresh mid-turn shows the answer so far instead of a blank panel.
CREATE TABLE app_lm_assistant_event (
    -- bigserial, as app_lm_audit_event uses: high volume, always read in order, never referenced.
    id          bigserial   PRIMARY KEY,
    turn_id     uuid        NOT NULL REFERENCES app_lm_assistant_turn (id) ON DELETE CASCADE,

    -- Monotonic within a turn and allocated by the writer, which is single per turn. The unique
    -- index below is the guard rather than the mechanism: two appends racing on one number is a bug,
    -- and it should fail loudly instead of silently reordering a conversation.
    seq         integer     NOT NULL,

    -- No CHECK, following app_lm_audit_event.event_type. A closed vocabulary belongs in a CHECK —
    -- `status` above is one — but the kinds a turn emits will grow with every tool the assistant
    -- learns, and a migration per kind would buy nothing: nothing joins on this column, and an
    -- unknown kind is already handled, because a client that half-understands the stream has to fall
    -- back to refetching anyway.
    kind        varchar(32) NOT NULL,

    -- Streamed text arrives batched into chunks of a few hundred milliseconds rather than a row per
    -- token: DB_POOL_MAX is 5 and a row per token would make this the largest write source in the
    -- application. Tool calls, proposals and errors carry their own shape.
    payload     jsonb       NOT NULL DEFAULT '{}'::jsonb,

    occurred_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT app_lm_assistant_event_seq_uk UNIQUE (turn_id, seq)
);

-- Immutable once written, enforced rather than assumed: `seq` is the SSE replay cursor, so a writer
-- that edits an event in place does not corrupt one row, it silently reorders a conversation for
-- every reader still catching up.
--
-- UPDATE only, which is where this parts company with app_lm_audit_event's append-only trigger
-- (V1) that the `kind` column above borrows from. That table is the permanent audit trail and
-- refuses DELETE and TRUNCATE too. This one is a replay buffer: the FK above is ON DELETE CASCADE
-- so removing a thread removes its events, and a retention sweep of turns long finished is expected
-- rather than forbidden. Blocking DELETE here would break both.
CREATE OR REPLACE FUNCTION app_lm_assistant_event_is_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'app_lm_assistant_event is immutable once written (attempted %)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER app_lm_assistant_event_immutable
    BEFORE UPDATE ON app_lm_assistant_event
    FOR EACH STATEMENT EXECUTE FUNCTION app_lm_assistant_event_is_immutable();

COMMENT ON TABLE app_lm_assistant_thread IS
    'One assistant conversation, owned by the user who started it. project_id is the mandate it was asked about, not its owner.';
COMMENT ON TABLE app_lm_assistant_turn IS
    'One exchange: the question, the settled answer, and the actor and audit context the background worker cannot resolve for itself.';
COMMENT ON TABLE app_lm_assistant_event IS
    'Append-only log of a running turn, read back by (turn_id, seq) so a dropped SSE stream resumes rather than restarts.';
COMMENT ON COLUMN app_lm_assistant_turn.actor_user_id IS
    'Who the turn is authorised as. The guard beans still re-read the database on every tool call; this only says whose membership to read.';
COMMENT ON COLUMN app_lm_assistant_event.seq IS
    'Monotonic within a turn. The SSE cursor; a reconnect replays from the last value it received.';

-- ── The sixth door ────────────────────────────────────────────────────────────
--
-- A company the assistant proposes and a person accepts is filed through the path the plugin, the
-- add-by-hand form, the edit form, the spreadsheet and Bright Data already use, so it needs no write
-- path of its own — only a name for the door it came through. Same shape as V47 adding 'CSV'.
--
-- app_lm_project_triage_company_apollo_source_chk is untouched and still correct: it constrains
-- STRATEGY rows to carry an apollo_account_id, and an assistant-sourced company may or may not
-- resolve to one.
ALTER TABLE app_lm_project_triage_company
    DROP CONSTRAINT app_lm_project_triage_company_source_chk;

ALTER TABLE app_lm_project_triage_company
    ADD CONSTRAINT app_lm_project_triage_company_source_chk
        CHECK (source IN ('STRATEGY', 'MANUAL', 'EXTENSION', 'CSV', 'ASSISTANT'));
