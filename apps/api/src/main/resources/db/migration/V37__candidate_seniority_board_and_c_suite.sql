-- Seniority gains a board tier, and the top executive tier stops being called N.
--
-- V36 wrote the ladder as N / N-1 / N-2 / N-3, which reads as "the chief executive and the distances
-- below them". A mandate is also written for board seats, and a board seat is not a distance from the
-- CEO — it is on the other side of the executive line. So the two tiers above the line are named
-- rather than numbered: BOARD and C_SUITE, then N-1 downwards as before.
--
-- Existing rows recorded as N were the executive committee, which is C_SUITE.

ALTER TABLE app_lm_project_candidate
    DROP CONSTRAINT app_lm_project_candidate_seniority_chk;

UPDATE app_lm_project_candidate SET seniority_level = 'C_SUITE' WHERE seniority_level = 'N';

ALTER TABLE app_lm_project_candidate
    ADD CONSTRAINT app_lm_project_candidate_seniority_chk
    CHECK (seniority_level IN ('BOARD', 'C_SUITE', 'N_MINUS_1', 'N_MINUS_2', 'N_MINUS_3'));
