-- An executive left with a company name and no company — their company removed while they were mapped at it, the
-- company added back as a new row that nothing re-attached them to, or a person added from the Candidates page under
-- a name alone — is put back under the company the position holds under exactly that name, at that company's stage,
-- as a filing would put them. Only where the position holds one such company: a name two rows share is left alone
-- rather than guessed.
--
-- From here removing a company takes its executives (after a warning), so nobody is stranded this way again.

CREATE TEMPORARY TABLE stranded_executive ON COMMIT DROP AS
SELECT c.id AS candidate_id, c.person_id, c.project_id, c.triage_status AS from_stage,
       t.id AS triage_company_id, t.company_name, t.status AS to_stage
  FROM app_lm_project_candidate c
  JOIN app_lm_person me ON me.id = c.person_id
  JOIN app_lm_project_triage_company t
    ON t.project_id = c.project_id
   AND lower(t.company_name) = lower(btrim(c.company_name))
 WHERE c.triage_company_id IS NULL
   AND c.company_name IS NOT NULL
   AND (SELECT count(*)
          FROM app_lm_project_triage_company same_name
         WHERE same_name.project_id = c.project_id
           AND lower(same_name.company_name) = lower(btrim(c.company_name))) = 1
   -- One name per company, the rule the app's filing holds: a namesake already there keeps this one apart.
   AND NOT EXISTS (SELECT 1
                     FROM app_lm_project_candidate held
                     JOIN app_lm_person namesake ON namesake.id = held.person_id
                    WHERE held.project_id = c.project_id
                      AND held.triage_company_id = t.id
                      AND lower(namesake.full_name) = lower(me.full_name));

-- The stage line a company's move writes, where the stage changes; nobody did it, so the actor is null.
INSERT INTO app_lm_person_activity (workspace_id, person_id, project_id, project_title, actor_user_id, kind, details)
SELECT p.workspace_id, s.person_id, s.project_id, pr.position_title, NULL, 'TRIAGE_STATUS_CHANGED',
       jsonb_build_object('from', CASE s.from_stage WHEN 'IN_UNIVERSE' THEN 'inUniverse'
                                                    WHEN 'SHORTLISTED' THEN 'shortlisted'
                                                    ELSE 'declined' END,
                          'to', CASE s.to_stage WHEN 'IN_UNIVERSE' THEN 'inUniverse'
                                                WHEN 'SHORTLISTED' THEN 'shortlisted'
                                                ELSE 'declined' END,
                          'withCompany', true)
  FROM stranded_executive s
  JOIN app_lm_person p ON p.id = s.person_id
  JOIN app_lm_project pr ON pr.id = s.project_id
 WHERE s.from_stage <> s.to_stage;

UPDATE app_lm_project_candidate c
   SET triage_company_id = s.triage_company_id,
       company_name = s.company_name,
       triage_status = s.to_stage
  FROM stranded_executive s
 WHERE c.id = s.candidate_id;

-- Someone is mapped there now, so "No executive found" no longer holds, as a new mapping clears it in the app.
UPDATE app_lm_project_triage_company t
   SET no_executive_found = false
 WHERE t.no_executive_found
   AND t.id IN (SELECT triage_company_id FROM stranded_executive);
