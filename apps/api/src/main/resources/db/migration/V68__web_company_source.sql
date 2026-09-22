-- ── The seventh door ──────────────────────────────────────────────────────────
--
-- A company AI Research found on the open web and a person read and filed. Same shape as V47 adding
-- 'CSV' and V65 adding 'ASSISTANT': no write path of its own, only a name for the door. It is kept
-- distinct from ASSISTANT because that badge says a conversation proposed the company, and this one
-- says a grounded search did — different acts, and the Source column is where a consultant reads
-- which.
--
-- app_lm_project_triage_company_apollo_source_chk is untouched, and that is exactly what this
-- feature leans on rather than an oversight: it says a STRATEGY row must carry an apollo_account_id,
-- and discovery honours it by filing the two halves of an answer through two doors. A discovered
-- company the universe does carry goes through POST /triage/bulk with its id and lands as STRATEGY;
-- one it does not carry goes through POST /triage/capture with no id at all and lands as WEB.
-- Widening that constraint would break the guarantee, not extend it.
--
-- app_lm_project_candidate_source_chk (V36) is deliberately NOT widened. Discovery proposes
-- companies, never executives, so a 'WEB' spelling there would be one the schema permits and no code
-- can produce — a claim nothing tests. It belongs to whichever issue first files a web-sourced
-- person.

ALTER TABLE app_lm_project_triage_company
    DROP CONSTRAINT app_lm_project_triage_company_source_chk;

ALTER TABLE app_lm_project_triage_company
    ADD CONSTRAINT app_lm_project_triage_company_source_chk
        CHECK (source IN ('STRATEGY', 'MANUAL', 'EXTENSION', 'CSV', 'ASSISTANT', 'WEB'));

-- V34 wrote this comment for three doors and V47 rewrote it for four; V65 added a fifth without
-- touching it. Brought back into line with the constraint above.
COMMENT ON COLUMN app_lm_project_triage_company.source IS
    'Which door the company came through: STRATEGY (Apollo universe), MANUAL (typed in), EXTENSION (browser plugin), CSV (spreadsheet import), ASSISTANT (accepted proposal), WEB (AI Research).';
