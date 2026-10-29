-- Shortlisted is no longer a Companies stage: In universe and Declined are the two a position files at. What was
-- left there is discarded as the app's own removals would discard it, and both stage columns hold only the two.
-- (The pipeline status of the same name, app_lm_workspace_candidate_stage, is another thing and is untouched.)

-- An executive listed at Shortlisted is removed from the position, as Remove from position does: the person stays
-- with the workspace, and the line saying which position let them go is written before the mapping goes. The
-- mapping's scorecard (and V91's frozen photo and contact copies) cascade; tasks, notifications, outreach runs and
-- messages, recordings and interview plans keep their rows with candidate_id set null, and a live run stops at its
-- next send as UNMAPPED.
INSERT INTO app_lm_person_activity (workspace_id, person_id, project_id, project_title, actor_user_id, kind)
SELECT p.workspace_id, c.person_id, c.project_id, pr.position_title, NULL, 'UNMAPPED'
  FROM app_lm_project_candidate c
  JOIN app_lm_person p ON p.id = c.person_id
  JOIN app_lm_project pr ON pr.id = c.project_id
 WHERE c.triage_status = 'SHORTLISTED';

DELETE FROM app_lm_project_candidate WHERE triage_status = 'SHORTLISTED';

-- A company at Shortlisted is removed from the position, as Remove from position does: its notes cascade, and an
-- executive still mapped at it (one moved to In universe or Declined alone) keeps their mapping, unassigned under
-- the company name snapshotted on their row.
DELETE FROM app_lm_project_triage_company WHERE status = 'SHORTLISTED';

ALTER TABLE app_lm_project_triage_company DROP CONSTRAINT app_lm_project_triage_company_status_chk;
ALTER TABLE app_lm_project_triage_company
    ADD CONSTRAINT app_lm_project_triage_company_status_chk CHECK (status IN ('IN_UNIVERSE', 'DECLINED'));

ALTER TABLE app_lm_project_candidate DROP CONSTRAINT app_lm_project_candidate_triage_status_chk;
ALTER TABLE app_lm_project_candidate
    ADD CONSTRAINT app_lm_project_candidate_triage_status_chk CHECK (triage_status IN ('IN_UNIVERSE', 'DECLINED'));

COMMENT ON COLUMN app_lm_project_candidate.triage_status IS
    'The Companies stage this executive is listed under (In universe / Declined); their company''s at filing.';
