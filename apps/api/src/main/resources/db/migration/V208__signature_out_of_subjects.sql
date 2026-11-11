-- V207's companion for the subject lines: a first email's subject could carry {{signature}} as typed text, which a
-- step's save now refuses (docs/email-signatures.md, revised 2026-11-11). The token is taken out of every stored first
-- subject and every frozen one, the spaces around it closed; a step subject left with nothing is none.

UPDATE app_lm_outreach_sequence_step
SET subject = NULLIF(btrim(regexp_replace(
        regexp_replace(subject, '\{\{\s*signature\s*(\\?\|[^{}|\n]{1,60})?\s*\}\}', '', 'g'),
        '\s+', ' ', 'g')), '')
WHERE subject ~ '\{\{\s*signature\s*(\\?\|[^{}|\n]{1,60})?\s*\}\}';

UPDATE app_lm_outreach_enrollment
SET first_subject = btrim(regexp_replace(
        regexp_replace(first_subject, '\{\{\s*signature\s*(\\?\|[^{}|\n]{1,60})?\s*\}\}', '', 'g'),
        '\s+', ' ', 'g'))
WHERE first_subject ~ '\{\{\s*signature\s*(\\?\|[^{}|\n]{1,60})?\s*\}\}';
