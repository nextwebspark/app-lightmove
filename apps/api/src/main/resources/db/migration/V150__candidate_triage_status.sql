-- An executive stands at a stage of their own (#903): moving or declining one person at a company no longer takes
-- every colleague with them. A company's own move still carries everyone mapped at it; a person moved alone keeps
-- their stage until they are moved again or remapped to another company, whose stage they then take.
ALTER TABLE app_lm_project_candidate
    ADD COLUMN triage_status varchar(16) NOT NULL DEFAULT 'IN_UNIVERSE'
        CONSTRAINT app_lm_project_candidate_triage_status_chk
        CHECK (triage_status IN ('IN_UNIVERSE', 'SHORTLISTED', 'DECLINED'));

COMMENT ON COLUMN app_lm_project_candidate.triage_status IS
    'The Companies stage this executive is listed under (In universe / Shortlisted / Declined); their company''s at filing.';

UPDATE app_lm_project_candidate c
   SET triage_status = t.status
  FROM app_lm_project_triage_company t
 WHERE t.id = c.triage_company_id
   AND t.status <> 'IN_UNIVERSE';

-- A stage page reads its people by stage, and its companies by "the company is here, or one of its people is".
CREATE INDEX app_lm_project_candidate_triage_status_idx
    ON app_lm_project_candidate (project_id, triage_status, triage_company_id);
