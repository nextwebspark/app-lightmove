-- The client registry becomes a first-class screen, and its representatives get real, scoped access.
--
-- Three things land together because they reshape the same client aggregate:
--
--   1. The client record grows up. It was a bare name to hang mandates off (V5); it now carries the
--      registry the Clients screen edits — sector, domain, an off-limits note — and its provenance in
--      the company universe. A client is either picked from app_lm_companies (its rebuild-stable
--      (source, source_id) key is stored, exactly as Strategy stores a company list) or typed in as a
--      custom record. app_lm_companies is ETL-owned and unwritable here, so a "new company" lives on
--      the client row itself, never upstream.
--
--   2. Client representatives. A client-side contact who represents a client record. They belong to the
--      client, independent of any mandate — the Clients drawer names them before a project exists — so
--      the row is keyed by client, not project.
--
--   3. Read-only project access. A representative is later attached to a mandate as a PROJECT CLIENT
--      seat and reads it through the new WORK_VIEW action. The workspace CLIENT role grants nothing;
--      this migration also re-scopes the invitation groundwork — a CLIENT invite was tied to a project
--      (V6); it is now tied to a client.

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Enrich the client record. Extend in place: app_lm_project.client_id still FKs this table, so a
--    parallel table would only force a needless backfill. The (source, source_id) pair is the
--    universe's rebuild-stable key, deliberately NOT a foreign key — app_lm_companies is refreshed
--    wholesale by the pipeline, and V14 keys strategy lists the same way.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE app_lm_client
    ADD COLUMN sector            varchar(96),
    ADD COLUMN domain            varchar(160),
    ADD COLUMN off_limits_note   text,
    ADD COLUMN company_source    text,
    ADD COLUMN company_source_id text;

-- Provenance is all-or-nothing: a universe-backed client carries both key halves, a custom one neither.
ALTER TABLE app_lm_client
    ADD CONSTRAINT app_lm_client_company_key_chk
        CHECK ((company_source IS NULL) = (company_source_id IS NULL));

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Client representatives. A lifecycle row (INVITED → ACTIVE → REVOKED), so an entity, not an
--    element collection: it gains a user id on accept and links the invitation that minted it.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE app_lm_client_representative (
    id            uuid PRIMARY KEY      DEFAULT gen_random_uuid(),
    -- Denormalised so the portal can scope by (workspace_id, user_id) without joining through client.
    workspace_id  uuid          NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    client_id     uuid          NOT NULL REFERENCES app_lm_client (id) ON DELETE CASCADE,
    full_name     varchar(160)  NOT NULL,
    position      varchar(160),
    email         varchar(320)  NOT NULL,
    status        varchar(16)   NOT NULL DEFAULT 'INVITED'
        CONSTRAINT app_lm_client_rep_status_chk CHECK (status IN ('INVITED', 'ACTIVE', 'REVOKED')),
    -- Null until they accept and an account exists; the invitation link that is still outstanding.
    user_id       uuid          REFERENCES app_lm_user (id),
    invitation_id uuid          REFERENCES app_lm_invitation (id) ON DELETE SET NULL,
    created_by    uuid          NOT NULL REFERENCES app_lm_user (id),
    created_at    timestamptz   NOT NULL DEFAULT now(),
    updated_at    timestamptz   NOT NULL DEFAULT now(),
    version       bigint        NOT NULL DEFAULT 0
);

CREATE INDEX app_lm_client_rep_client_idx  ON app_lm_client_representative (client_id);
-- The portal's scoping lookup: which client does this signed-in user represent?
CREATE INDEX app_lm_client_rep_ws_user_idx ON app_lm_client_representative (workspace_id, user_id);
-- One live representative per email per client; a revoked row is reused when the same address is re-invited.
CREATE UNIQUE INDEX app_lm_client_rep_email_uk
    ON app_lm_client_representative (client_id, lower(email)) WHERE status <> 'REVOKED';

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Re-scope the invitation groundwork from project to client. The V6 column and its CHECK were never
--    read by any code (nothing minted a CLIENT invite yet), so this replaces rather than migrates.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE app_lm_invitation DROP CONSTRAINT app_lm_invitation_client_project_chk;
ALTER TABLE app_lm_invitation DROP COLUMN project_id;
ALTER TABLE app_lm_invitation
    ADD COLUMN client_id uuid REFERENCES app_lm_client (id) ON DELETE CASCADE;

-- The live-invite uniqueness (V1) forbade two PENDING invites to one (workspace, email) — correct when
-- every invite was staff. A representative and a staff colleague can now share a work address, so the
-- index gains client_id, and NULLS NOT DISTINCT keeps the staff side (client_id NULL) collapsing to one
-- while letting a staff invite and a client-rep invite to the same email coexist.
DROP INDEX app_lm_invitation_pending_uk;
CREATE UNIQUE INDEX app_lm_invitation_pending_uk
    ON app_lm_invitation (workspace_id, email, client_id) NULLS NOT DISTINCT
    WHERE status = 'PENDING';

-- A CLIENT invitation names the client its representative joins; a staff invitation names none. The
-- CHECK compares against the seeded CLIENT role id, computed here — role ids are stable from V6 on.
DO $$
DECLARE
    client_role uuid;
BEGIN
    SELECT id INTO client_role FROM app_lm_role WHERE scope = 'WORKSPACE' AND name = 'CLIENT';
    EXECUTE format(
            'ALTER TABLE app_lm_invitation ADD CONSTRAINT app_lm_invitation_client_chk
             CHECK ((role_id = %L) = (client_id IS NOT NULL))', client_role);
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Read access is a project seat, not a workspace grant. A client representative is attached to a
--    mandate as a PROJECT CLIENT seat and reads it read-only through the new WORK_VIEW action — the
--    view half of WORK_EXECUTE, split out so a client can see a project's content without editing it.
--    The workspace CLIENT role grants nothing: a client is scoped entirely by the projects they sit on.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO app_lm_action (scope, name, description)
VALUES ('PROJECT', 'WORK_VIEW', 'Read a project''s team-only content — strategy, brief, sourcing');

-- Every seated role can read; only the staff roles keep WORK_EXECUTE (the writes). CLIENT gets WORK_VIEW
-- alone, so an attached representative views the mandate and can change nothing.
INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM app_lm_role r
JOIN app_lm_action a ON a.scope = 'PROJECT' AND a.name = 'WORK_VIEW'
WHERE r.scope = 'PROJECT' AND r.name IN ('ADMIN', 'LEAD', 'RESEARCHER', 'CLIENT');

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Keep updated_at honest on the new table.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TRIGGER app_lm_client_representative_touch BEFORE UPDATE ON app_lm_client_representative
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();
