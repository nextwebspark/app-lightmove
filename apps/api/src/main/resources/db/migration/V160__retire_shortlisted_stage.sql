-- Shortlisted is no longer a Companies stage: In universe and Declined are the two a position files at. What was
-- left there is discarded as the app's own removals would discard it.
-- (The pipeline status of the same name, app_lm_workspace_candidate_stage, is another thing and is untouched.)
--
-- Expand only: both stage CHECKs still admit SHORTLISTED, because the previous revision keeps serving while this
-- deploys and may still write it. The application no longer loads such a row (TriageCompany and Candidate carry an
-- @SQLRestriction against it); a later migration deletes any straggler again and narrows both CHECKs.

-- One audit line per position that loses anything, written before the rows go so it can count them. Nobody did it:
-- the actor is null, as a migration's is.
WITH removed_companies AS (
    SELECT project_id, count(*) AS companies
      FROM app_lm_project_triage_company
     WHERE status = 'SHORTLISTED'
     GROUP BY project_id
), unmapped_executives AS (
    SELECT project_id, count(*) AS executives
      FROM app_lm_project_candidate
     WHERE triage_status = 'SHORTLISTED'
     GROUP BY project_id
)
INSERT INTO app_lm_audit_event (event_type, outcome, workspace_id, target_type, target_id, metadata)
SELECT 'SHORTLISTED_STAGE_RETIRED', 'SUCCESS', pr.workspace_id, 'project', pr.id::text,
       jsonb_build_object('migration', 'V160',
                          'companiesRemoved', coalesce(rc.companies, 0),
                          'executivesUnmapped', coalesce(ue.executives, 0))
  FROM app_lm_project pr
  LEFT JOIN removed_companies rc ON rc.project_id = pr.id
  LEFT JOIN unmapped_executives ue ON ue.project_id = pr.id
 WHERE rc.project_id IS NOT NULL OR ue.project_id IS NOT NULL;

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
