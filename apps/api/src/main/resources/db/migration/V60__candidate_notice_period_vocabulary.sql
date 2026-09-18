-- One spelling per notice period on an executive's profile.
--
-- The drawer asked for a notice period with a text box, so the column holds "3 months", "3 Months",
-- "90 days" and "negotiable" for the same fact, and nothing can count them. It asks with a picker
-- now — None, 1, 2, 3 or 6 months — and stores the option's own label, so this is the one-off
-- catch-up for the rows typed before it existed, in the shape V50 used for country spellings.
--
-- Only exact equivalents are folded: ninety days and three months are one period. A row stating
-- something the picker does not offer — six weeks, "negotiable", a sentence — is left exactly as it
-- is and stays offered in the select as recorded, because an unresolvable spelling is a fact
-- somebody entered, not a row to guess at.
--
-- Deliberately no CHECK on the column, and none is coming: V39 dropped the one CHECK this schema had
-- on a notice period the day a third option arrived, and a section save replays the whole
-- compensation object, so a constraint would refuse to save a row nobody had touched.
--
-- One statement, and deliberately no temp table — `harden.sql` leaves lm_migrate without the
-- TEMPORARY privilege, which a local Testcontainers run would never catch. A data-modifying CTE
-- needs no privilege at all.

WITH spelling (typed, canonical) AS (VALUES
    ('none', 'None'),
    ('nil', 'None'),
    ('no notice', 'None'),
    ('no notice period', 'None'),
    ('immediate', 'None'),
    ('immediately', 'None'),
    ('available immediately', 'None'),
    ('asap', 'None'),
    ('0', 'None'),
    ('0 months', 'None'),
    ('1 month', '1 month'),
    ('1month', '1 month'),
    ('1 months', '1 month'),
    ('1m', '1 month'),
    ('1 m', '1 month'),
    ('1mo', '1 month'),
    ('1 mo', '1 month'),
    ('1 mth', '1 month'),
    ('1 mths', '1 month'),
    ('one month', '1 month'),
    ('30 days', '1 month'),
    ('4 weeks', '1 month'),
    ('2 months', '2 months'),
    ('2months', '2 months'),
    ('2 month', '2 months'),
    ('2m', '2 months'),
    ('2 m', '2 months'),
    ('2mo', '2 months'),
    ('2 mo', '2 months'),
    ('2 mths', '2 months'),
    ('two months', '2 months'),
    ('60 days', '2 months'),
    ('8 weeks', '2 months'),
    ('3 months', '3 months'),
    ('3months', '3 months'),
    ('3 month', '3 months'),
    ('3m', '3 months'),
    ('3 m', '3 months'),
    ('3mo', '3 months'),
    ('3 mo', '3 months'),
    ('3 mths', '3 months'),
    ('three months', '3 months'),
    ('90 days', '3 months'),
    ('12 weeks', '3 months'),
    ('6 months', '6 months'),
    ('6months', '6 months'),
    ('6 month', '6 months'),
    ('6m', '6 months'),
    ('6 m', '6 months'),
    ('6mo', '6 months'),
    ('6 mo', '6 months'),
    ('6 mths', '6 months'),
    ('six months', '6 months'),
    ('180 days', '6 months'),
    ('26 weeks', '6 months')
),
rewritten AS (
    UPDATE app_lm_project_candidate AS target
    SET notice_period = spelling.canonical
    FROM spelling
    WHERE lower(btrim(target.notice_period)) = spelling.typed
      AND target.notice_period <> spelling.canonical
    RETURNING 1
)
SELECT count(*) FROM rewritten;
