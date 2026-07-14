-- The signup wizard, held for a user who has not proved their mailbox yet.
--
-- Signup step 2 used to *commit*: it created the workspace there and then, and therefore required a
-- verified address — which meant an unverified user filling in "About your organization" got a 403 and
-- a dead end halfway through their own signup. The whole wizard should be completable; what must not
-- happen is a workspace coming into existence on the strength of an address nobody has proved.
--
-- So the intent is recorded here and materialised at verification. All three steps run; nothing exists
-- on the firm's domain until someone reads the email we sent them.
--
-- ── Why a separate table, and not `status = 'DRAFT'` on app_lm_workspace ──────
--
-- Because a draft workspace *is a row in the workspace table*, and the only thing then keeping it out
-- of "which workspaces are already on my domain?" is a WHERE clause. Forget that predicate once — here,
-- or in the Projects screen someone writes next month — and an unverified impostor's organisation is
-- offered to the real employees of the firm they are impersonating.
--
-- Keeping the intent somewhere that is not the workspace table makes that unrepresentable rather than
-- merely filtered. The domain lookup physically cannot return one of these. It is the same instinct as
-- storing a token's hash rather than the token and a `revoked` flag.

CREATE TABLE app_lm_pending_onboarding (
    id             uuid PRIMARY KEY     DEFAULT gen_random_uuid(),

    -- One at a time. Re-submitting step 2 (the wizard's Back button) updates this row rather than
    -- accumulating abandoned intents, and a user who has already joined somewhere has no pending one.
    user_id        uuid         NOT NULL UNIQUE REFERENCES app_lm_user (id) ON DELETE CASCADE,

    -- The two branches of step 2. They are stored together because a user has at most one intent and it
    -- is exactly one of these — splitting them into two tables would make "one at a time" a thing you
    -- enforce in code instead of a UNIQUE constraint.
    kind           varchar(16)  NOT NULL
        CONSTRAINT app_lm_pending_onboarding_kind_chk CHECK (kind IN ('CREATE', 'JOIN')),

    -- ── kind = 'CREATE' ──
    name           varchar(160),
    company_size   varchar(32),
    primary_region varchar(32),
    team_focus     varchar(32),
    job_title      varchar(120),

    -- Step 3's invitations, held until there is a workspace to invite anyone into: [{"email":…,"role":…}].
    -- No invitation is *sent* from here — an unverified user must not be able to make LightMove email
    -- strangers on their say-so.
    invitations    jsonb        NOT NULL DEFAULT '[]'::jsonb,

    -- ── kind = 'JOIN' ──
    -- Cascades: if the workspace they wanted to join is deleted before they verify, the intent is
    -- meaningless and goes with it.
    workspace_id   uuid REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    requested_role varchar(32)
        CONSTRAINT app_lm_pending_onboarding_role_chk
            CHECK (requested_role IS NULL OR requested_role IN ('ADMIN', 'CONSULTANT', 'RESEARCHER')),

    -- Dies with the verification token that would have redeemed it. The intent and the proof of intent
    -- have the same lifetime: a link that no longer works cannot bring a three-week-old draft to life.
    expires_at     timestamptz  NOT NULL,

    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    version        bigint       NOT NULL DEFAULT 0,

    -- Each branch carries what it actually needs. Without these a JOIN row could be written with no
    -- workspace to join, and would fail at verification — long after the request that created it.
    CONSTRAINT app_lm_pending_onboarding_create_chk
        CHECK (kind <> 'CREATE' OR name IS NOT NULL),
    CONSTRAINT app_lm_pending_onboarding_join_chk
        CHECK (kind <> 'JOIN' OR (workspace_id IS NOT NULL AND requested_role IS NOT NULL))
);

COMMENT ON TABLE app_lm_pending_onboarding IS
    'A completed signup wizard awaiting email verification. Materialised into a workspace or a join request by VerificationService; never visible to the domain lookup.';

CREATE INDEX app_lm_pending_onboarding_expires_idx ON app_lm_pending_onboarding (expires_at);

CREATE TRIGGER app_lm_pending_onboarding_touch
    BEFORE UPDATE
    ON app_lm_pending_onboarding
    FOR EACH ROW
EXECUTE FUNCTION app_lm_touch_updated_at();
