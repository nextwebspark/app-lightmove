-- The project tier loses its ADMIN role. LEAD becomes the mandate owner: it inherits TEAM_MANAGE and
-- POSITION_UNLOCK, the two actions only ADMIN held, and the last-admin invariant becomes a last-lead
-- one. Two roles that mean "runs this mandate" was a distinction nobody made on the screen and nobody
-- could explain, so it goes.
--
-- With it goes multi-role staffing: a seat now holds exactly one staff role, LEAD or RESEARCHER. That
-- rule is enforced in the service and the HTTP contract, not by a constraint — app_lm_project_member_role
-- stays a set on purpose, so re-admitting a second role later is a code change and not a migration.
-- What this file does is make the existing rows obey it.
--
-- CLIENT is untouched throughout. It is granted by attaching a client representative, never by seating
-- someone on the team, and a dual-role person (staff seat + client seat on the same mandate) keeps both.
--
-- V18 belongs to the CoreSignal POC branch — slots are skipped, not reused, so a shared local database
-- stays bootable across branch switches.

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. LEAD inherits what ADMIN alone could do.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM app_lm_role r
JOIN app_lm_action a ON a.scope = 'PROJECT' AND a.name IN ('TEAM_MANAGE', 'POSITION_UNLOCK')
WHERE r.scope = 'PROJECT' AND r.name = 'LEAD'
ON CONFLICT DO NOTHING;

UPDATE app_lm_role
SET description = 'Owns the mandate: runs the search, seats the team, decides client access'
WHERE scope = 'PROJECT' AND name = 'LEAD';

-- V6 seeded this one as "becoming its project admin". The catalog is what an operator queries to find
-- out what a permission means, so it must not describe a role that is about to stop existing.
UPDATE app_lm_action
SET description = 'Start a mandate, becoming its lead'
WHERE scope = 'WORKSPACE' AND name = 'PROJECT_CREATE';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Every ADMIN seat becomes a LEAD seat, before the role it holds disappears.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO app_lm_project_member_role (project_member_id, role_id)
SELECT pmr.project_member_id, (SELECT id FROM app_lm_role WHERE scope = 'PROJECT' AND name = 'LEAD')
FROM app_lm_project_member_role pmr
JOIN app_lm_role admin ON admin.id = pmr.role_id
                      AND admin.scope = 'PROJECT' AND admin.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- One staff role per seat, applied to the rows that predate the rule. LEAD wins over RESEARCHER: it is
-- strictly the larger grant, so nobody loses access to work they could do yesterday.
DELETE FROM app_lm_project_member_role researcher_row
USING app_lm_role researcher, app_lm_project_member_role lead_row, app_lm_role lead
WHERE researcher_row.role_id = researcher.id
  AND researcher.scope = 'PROJECT' AND researcher.name = 'RESEARCHER'
  AND lead_row.project_member_id = researcher_row.project_member_id
  AND lead_row.role_id = lead.id
  AND lead.scope = 'PROJECT' AND lead.name = 'LEAD';

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Retire PROJECT/ADMIN. Deleting the catalog row cascades its grants; the assignments have to go
--    first, because app_lm_project_member_role's FK is on (role_id, role_scope) with no cascade —
--    deliberately, so a role can never be dropped out from under a live seat unnoticed.
--
--    Nothing else should reference it: app_lm_invitation.role_id only ever carries workspace roles
--    (V6 backfilled it from the membership role, and the client CHECK pins the one exception). If some
--    row does, this DELETE fails and takes the deploy with it — the right outcome, since silently
--    repointing an invitation would change what somebody was invited to be.
-- ─────────────────────────────────────────────────────────────────────────────

DELETE FROM app_lm_project_member_role pmr
USING app_lm_role admin
WHERE pmr.role_id = admin.id AND admin.scope = 'PROJECT' AND admin.name = 'ADMIN';

DELETE FROM app_lm_role WHERE scope = 'PROJECT' AND name = 'ADMIN';

-- A project that had neither an ADMIN nor a LEAD seat still has no lead after this, exactly as V6 left
-- it: reachable only through the workspace-admin bypass, and impossible for anything created since.
