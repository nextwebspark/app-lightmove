-- Gender on a mapped executive, so the report's diversity chapter can state it.
--
-- Recorded, never inferred. The chapter this feeds used to omit gender entirely on the grounds that
-- guessing it from a name would state a guess as a finding — that argument still stands, and this
-- column is what replaces the guess: a researcher sets it or nobody does.
--
-- Nullable with no default, and NULL is not a fourth value. "Not recorded" and "recorded as other"
-- are different facts about a person, and a chapter that folded one into the other would report a
-- pool it had not measured. Every existing row is NULL, which is the truth about every one of them.
ALTER TABLE app_lm_project_candidate
    ADD COLUMN gender varchar(16)
        CONSTRAINT app_lm_project_candidate_gender_chk
            CHECK (gender IN ('FEMALE', 'MALE', 'OTHER'));
