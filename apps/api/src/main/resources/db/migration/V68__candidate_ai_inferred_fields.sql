-- Which of nationality/gender/years_experience currently hold a value an AI inference proposed, not
-- yet reviewed by a researcher (issue #458).
--
-- A captured executive's background is now read off their profile automatically instead of waiting
-- for someone to type it in, including gender — which this table's own V56 comment once said would
-- never be inferred. This column is what keeps that promise in spirit even though the rule itself no
-- longer holds literally: a value written from here is flagged until a researcher's own edit changes
-- it, so a report never reads an unreviewed guess as a fact somebody recorded.
--
-- A small jsonb list rather than three booleans, for V45/V46's reason: the values these keys ever hold
-- ("nationality", "gender", "yearsExperience") are a fixed, tiny set, but keeping them as data rather
-- than three more schema columns is one migration and one Java field instead of three of each.
ALTER TABLE app_lm_project_candidate
    ADD COLUMN ai_inferred_fields jsonb NOT NULL DEFAULT '[]';
