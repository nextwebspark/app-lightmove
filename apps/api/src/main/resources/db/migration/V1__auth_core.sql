-- LightMove auth + tenancy core.
--
-- Naming: every table is prefixed app_lm_.
-- Identity: uuid primary keys (gen_random_uuid) — they are safe to expose in URLs and do not leak
--   row counts the way a bigserial would.
-- Secrets: no raw token is ever stored. Refresh, verification and invitation tokens are 256-bit
--   random values; only their SHA-256 hash lands here, so a database leak does not yield usable
--   credentials.
-- Case: emails, domains and slugs are stored lower-cased, enforced by a CHECK rather than by the
--   citext type. Two reasons. citext reports itself to JDBC as OTHER, so Hibernate's schema
--   validation rejects it against a String field — solvable, but only by teaching the ORM a custom
--   type. And citext would merely *hide* a code path that forgot to normalise, where the CHECK makes
--   that path fail loudly. Normalising at the boundary and asserting it here is the stricter contract.


-- ─────────────────────────────────────────────────────────────────────────────
-- Users
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE app_lm_user (
    id                     uuid PRIMARY KEY     DEFAULT gen_random_uuid(),

    -- Always lower-cased (see the CHECK below), so a plain UNIQUE is a case-insensitive UNIQUE.
    email                  varchar(255) NOT NULL UNIQUE
        CONSTRAINT app_lm_user_email_lower_chk CHECK (email = lower(email)),
    -- Null for users who only ever signed in with Google; they have no local password to check.
    password_hash          text,
    full_name              varchar(160) NOT NULL,

    -- Job title, e.g. "Managing Partner". Distinct from the workspace role in app_lm_workspace_member:
    -- the title is how a person describes themselves, the role is what they are allowed to do.
    title                  varchar(120),
    avatar_url             text,
    timezone               varchar(64)  NOT NULL DEFAULT 'Asia/Dubai',
    locale                 varchar(16)  NOT NULL DEFAULT 'en',

    status                 varchar(32)  NOT NULL DEFAULT 'PENDING_VERIFICATION'
        CONSTRAINT app_lm_user_status_chk
            CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED')),
    email_verified_at      timestamptz,

    -- Brute-force defence. locked_until is authoritative; failed_login_attempts only feeds the
    -- backoff calculation and is reset on any successful login.
    failed_login_attempts  int          NOT NULL DEFAULT 0,
    locked_until           timestamptz,
    last_login_at          timestamptz,

    -- GDPR: proof of what the user agreed to, and to which version of it.
    terms_accepted_at      timestamptz,
    privacy_policy_version varchar(32),

    created_at             timestamptz  NOT NULL DEFAULT now(),
    updated_at             timestamptz  NOT NULL DEFAULT now(),
    version                bigint       NOT NULL DEFAULT 0
);

COMMENT ON TABLE app_lm_user IS 'Person who can sign in. Tenant-agnostic: membership lives in app_lm_workspace_member.';


-- Federated sign-in. A row per (provider, external account) linked to one local user, so the same
-- person can sign in with a password and with Google and remain one user.
CREATE TABLE app_lm_user_identity (
    id               uuid PRIMARY KEY    DEFAULT gen_random_uuid(),
    user_id          uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    provider         varchar(32) NOT NULL
        CONSTRAINT app_lm_user_identity_provider_chk CHECK (provider IN ('LOCAL', 'GOOGLE')),
    provider_user_id varchar(255) NOT NULL,
    email            varchar(255),
    linked_at        timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT app_lm_user_identity_provider_uk UNIQUE (provider, provider_user_id)
);

CREATE INDEX app_lm_user_identity_user_idx ON app_lm_user_identity (user_id);


-- ─────────────────────────────────────────────────────────────────────────────
-- Tenancy: workspace + membership
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE app_lm_workspace (
    id               uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    name             varchar(160) NOT NULL,
    slug             varchar(64)  NOT NULL UNIQUE
        CONSTRAINT app_lm_workspace_slug_lower_chk CHECK (slug = lower(slug)),
    logo_mark        varchar(4),

    -- The domain of the address that created this workspace, e.g. nextwebspark.com.
    --
    -- Deliberately NOT unique. One firm can run several workspaces on the same domain — separate
    -- practices, separate regions, a partner spinning up their own. So the domain is a way of *finding*
    -- your colleagues, not a claim on them: at signup we show the user which workspaces already exist
    -- on their domain, and they either ask to join one (an admin approves) or create a new one.
    --
    -- Because LightMove refuses consumer email domains, this stays meaningful: everyone under
    -- nextwebspark.com works at NextWebSpark, even if they work in two different workspaces.
    email_domain     varchar(255) NOT NULL
        CONSTRAINT app_lm_workspace_domain_lower_chk CHECK (email_domain = lower(email_domain)),

    -- Captured during signup step 2; drives onboarding defaults and segmentation.
    company_size     varchar(32),
    primary_region   varchar(32),
    team_focus       varchar(32),

    -- Workspace-wide defaults, editable later in Settings → General.
    default_region   varchar(32)  NOT NULL DEFAULT 'GCC',
    default_currency varchar(8)   NOT NULL DEFAULT 'USD',

    plan             varchar(32)  NOT NULL DEFAULT 'FREE',
    status           varchar(32)  NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT app_lm_workspace_status_chk CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),

    created_by       uuid         NOT NULL REFERENCES app_lm_user (id),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    version          bigint       NOT NULL DEFAULT 0
);

COMMENT ON TABLE app_lm_workspace IS 'The tenant. Every workspace-scoped query filters on this id.';

-- Drives the "workspaces already on your domain" list a new signup is offered.
CREATE INDEX app_lm_workspace_domain_idx ON app_lm_workspace (email_domain) WHERE status = 'ACTIVE';


CREATE TABLE app_lm_workspace_member (
    id           uuid PRIMARY KEY    DEFAULT gen_random_uuid(),
    workspace_id uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    user_id      uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,

    role         varchar(32) NOT NULL
        CONSTRAINT app_lm_workspace_member_role_chk
            CHECK (role IN ('ADMIN', 'CONSULTANT', 'RESEARCHER')),

    -- PENDING_APPROVAL is how someone gets in without an invitation: they found their firm's workspace
    -- on their email domain and asked to join, and an admin has not yet said yes. It carries no access
    -- at all — a pending member cannot read a single row of workspace data. That is the point: sharing
    -- an employer's email domain is evidence someone works there, not a decision that they should see
    -- an executive-search pipeline. A human makes that decision.
    --
    -- An invited user skips this state entirely and lands ACTIVE: an admin naming them *is* the decision.
    status       varchar(32) NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT app_lm_workspace_member_status_chk
            CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'REJECTED', 'SUSPENDED', 'REMOVED')),

    -- Null while PENDING_APPROVAL — they have not joined yet, they have only asked.
    joined_at    timestamptz,
    -- Who approved (or rejected) them, and when. Compliance wants to know who let someone in.
    decided_by   uuid REFERENCES app_lm_user (id),
    decided_at   timestamptz,

    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    version      bigint      NOT NULL DEFAULT 0,

    CONSTRAINT app_lm_workspace_member_uk UNIQUE (workspace_id, user_id)
);

-- A user belongs to at most ONE organisation.
--
-- Note carefully what is unique here: user_id, NOT workspace_id. A workspace holds as many members as
-- it likes — workspace_id repeats freely down this table, which is how a firm has a whole team. What
-- cannot repeat is a user: alok@nextwebspark.com is in nextwebspark.com's workspace and in no other.
--
-- That follows from the domain rule. An email domain identifies exactly one organisation, so one
-- address in two organisations is a contradiction. Application code could be trusted to remember
-- that; the database is trusted to enforce it.
--
-- Partial, on ACTIVE only, so that someone removed from a workspace can later join another.
CREATE UNIQUE INDEX app_lm_workspace_member_single_org_per_user_uk
    ON app_lm_workspace_member (user_id)
    WHERE status = 'ACTIVE';

CREATE INDEX app_lm_workspace_member_user_idx ON app_lm_workspace_member (user_id);


CREATE TABLE app_lm_invitation (
    id                  uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    workspace_id        uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    email               varchar(255) NOT NULL
        CONSTRAINT app_lm_invitation_email_lower_chk CHECK (email = lower(email)),
    role                varchar(32) NOT NULL
        CONSTRAINT app_lm_invitation_role_chk CHECK (role IN ('ADMIN', 'CONSULTANT', 'RESEARCHER')),

    token_hash          varchar(64) NOT NULL UNIQUE,
    invited_by          uuid        NOT NULL REFERENCES app_lm_user (id),

    status              varchar(32) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT app_lm_invitation_status_chk
            CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    expires_at          timestamptz NOT NULL,
    accepted_at         timestamptz,
    accepted_by_user_id uuid REFERENCES app_lm_user (id),

    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0
);

-- One live invite per person per workspace. Re-inviting someone who already has a pending invite
-- should resend, not create a second row.
CREATE UNIQUE INDEX app_lm_invitation_pending_uk
    ON app_lm_invitation (workspace_id, email)
    WHERE status = 'PENDING';


-- ─────────────────────────────────────────────────────────────────────────────
-- Tokens
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE app_lm_verification_token (
    id          uuid PRIMARY KEY    DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL UNIQUE,
    purpose     varchar(32) NOT NULL
        CONSTRAINT app_lm_verification_token_purpose_chk
            CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX app_lm_verification_token_user_idx ON app_lm_verification_token (user_id, purpose);


-- Rotating refresh tokens. Every refresh issues a new row and revokes its predecessor, chaining them
-- through replaced_by_id within a family_id.
--
-- family_id is what makes theft detectable: if a token that has already been rotated away is
-- presented again, either the legitimate client or an attacker is replaying it, and we cannot tell
-- which — so the entire family is revoked and both parties must re-authenticate.
CREATE TABLE app_lm_refresh_token (
    id             uuid PRIMARY KEY    DEFAULT gen_random_uuid(),
    user_id        uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    token_hash     varchar(64) NOT NULL UNIQUE,
    family_id      uuid        NOT NULL,

    expires_at     timestamptz NOT NULL,
    revoked_at     timestamptz,
    revoked_reason varchar(32)
        CONSTRAINT app_lm_refresh_token_revoked_reason_chk
            CHECK (revoked_reason IN ('ROTATED', 'LOGOUT', 'REUSE_DETECTED', 'PASSWORD_CHANGED', 'ADMIN_REVOKED')),
    replaced_by_id uuid REFERENCES app_lm_refresh_token (id),

    -- Shown in Settings → Active sessions, and useful evidence when a family is revoked for reuse.
    user_agent     varchar(512),
    ip_address     varchar(45),

    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX app_lm_refresh_token_user_idx   ON app_lm_refresh_token (user_id);
CREATE INDEX app_lm_refresh_token_family_idx ON app_lm_refresh_token (family_id);


-- ─────────────────────────────────────────────────────────────────────────────
-- Audit
-- ─────────────────────────────────────────────────────────────────────────────

-- Append-only: an audit trail the application can rewrite is not an audit trail.
--
-- Enforcement cannot live here. Flyway runs as the application role, which therefore owns this table,
-- and a Postgres table owner can always re-grant itself any privilege it revokes. Real enforcement is
-- ops/cloudsql/harden.sql — run once as a superuser, it reassigns ownership away from lm_app and
-- leaves it only INSERT and SELECT. The trigger below is the belt to that braces: it blocks UPDATE
-- and DELETE regardless of who holds what grant.
CREATE TABLE app_lm_audit_event (
    id             bigserial PRIMARY KEY,
    occurred_at    timestamptz NOT NULL DEFAULT now(),

    event_type     varchar(64) NOT NULL,
    outcome        varchar(16) NOT NULL
        CONSTRAINT app_lm_audit_event_outcome_chk CHECK (outcome IN ('SUCCESS', 'FAILURE')),

    -- Nullable: a failed login has no authenticated actor, and signup has no workspace yet.
    actor_user_id  uuid,
    workspace_id   uuid,

    target_type    varchar(64),
    target_id      varchar(128),

    ip_address     varchar(45),
    user_agent     varchar(512),
    correlation_id varchar(64),

    -- Free-form context (failure reason, invited email, ...). Must never contain a credential.
    metadata       jsonb       NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX app_lm_audit_event_occurred_idx  ON app_lm_audit_event (occurred_at DESC);
CREATE INDEX app_lm_audit_event_actor_idx     ON app_lm_audit_event (actor_user_id, occurred_at DESC);
CREATE INDEX app_lm_audit_event_workspace_idx ON app_lm_audit_event (workspace_id, occurred_at DESC);
CREATE INDEX app_lm_audit_event_type_idx      ON app_lm_audit_event (event_type, occurred_at DESC);

CREATE OR REPLACE FUNCTION app_lm_audit_event_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'app_lm_audit_event is append-only (attempted %)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER app_lm_audit_event_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON app_lm_audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION app_lm_audit_event_is_append_only();


-- Projects (search mandates) are deliberately absent. The post-login screen this migration supports is
-- a placeholder showing the workspace's empty state; modelling a seven-stage pipeline before any
-- pipeline logic exists would be guessing at it. app_lm_project arrives with the Project screen.


-- ─────────────────────────────────────────────────────────────────────────────
-- updated_at maintenance
-- ─────────────────────────────────────────────────────────────────────────────

-- Kept in the database rather than in JPA so that a manual SQL fix or a future service in another
-- language cannot silently leave updated_at stale.
CREATE OR REPLACE FUNCTION app_lm_touch_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    tbl text;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['app_lm_user', 'app_lm_workspace', 'app_lm_workspace_member',
                               'app_lm_invitation']
        LOOP
            EXECUTE format(
                'CREATE TRIGGER %I_touch BEFORE UPDATE ON %I
                 FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at()', tbl, tbl);
        END LOOP;
END;
$$;
