-- An executive left with a company name and no company (their company removed while they were mapped at it, or a
-- Candidates page Add to position that recorded a name alone) is put back under the one company the position holds
-- under exactly that name. They keep the stage they are listed at, so nobody moves out of sight; a name two companies
-- share, or a namesake already at the company (or a second stranded one), is left alone. From here a removal takes its
-- executives, so nobody is stranded this way again.

CREATE TEMPORARY TABLE stranded_executive ON COMMIT DROP AS
WITH named_companies AS (
    SELECT id, project_id, company_name, lower(company_name) AS name_key,
           count(*) OVER (PARTITION BY project_id, lower(company_name)) AS holders
      FROM app_lm_project_triage_company
), candidates AS (
    SELECT c.id AS candidate_id, c.project_id, c.created_at, lower(btrim(c.company_name)) AS name_key,
           lower(p.full_name) AS person_key
      FROM app_lm_project_candidate c
      JOIN app_lm_person p ON p.id = c.person_id
     WHERE c.triage_company_id IS NULL
       AND c.company_name IS NOT NULL
), matched AS (
    SELECT s.candidate_id, t.id AS triage_company_id, t.company_name,
           row_number() OVER (PARTITION BY t.id, s.person_key ORDER BY s.created_at, s.candidate_id) AS name_rank,
           s.person_key, s.project_id
      FROM candidates s
      JOIN named_companies t ON t.project_id = s.project_id AND t.name_key = s.name_key AND t.holders = 1
)
SELECT m.candidate_id, m.triage_company_id, m.company_name
  FROM matched m
 WHERE m.name_rank = 1
   AND NOT EXISTS (SELECT 1
                     FROM app_lm_project_candidate held
                     JOIN app_lm_person namesake ON namesake.id = held.person_id
                    WHERE held.project_id = m.project_id
                      AND held.triage_company_id = m.triage_company_id
                      AND lower(namesake.full_name) = m.person_key);

UPDATE app_lm_project_candidate c
   SET triage_company_id = s.triage_company_id,
       company_name = s.company_name
  FROM stranded_executive s
 WHERE c.id = s.candidate_id;

-- Someone is mapped there now, so "No executive found" no longer holds, as a new mapping clears it in the app.
UPDATE app_lm_project_triage_company t
   SET no_executive_found = false
 WHERE t.no_executive_found
   AND t.id IN (SELECT triage_company_id FROM stranded_executive);
