-- A signature is never part of an email's words (docs/email-signatures.md, revised 2026-11-11): Add to sequence's
-- Include signature chooses it at Start, and every email of the run closes with it. The {{signature}} token a step
-- placed since #1277 is taken out of every stored step and every frozen first email, its gap closed to one blank line,
-- so no code path meets it again. A run already signed (signature_html) stays signed, now under each email's words.

UPDATE app_lm_outreach_sequence_step
SET body = btrim(regexp_replace(
        regexp_replace(body, '\{\{\s*signature\s*(\\?\|[^{}|\n]{1,60})?\s*\}\}', '', 'g'),
        '(\r?\n[ \t]*){3,}', E'\n\n', 'g'), E' \t\r\n')
WHERE body ~ '\{\{\s*signature\s*(\\?\|[^{}|\n]{1,60})?\s*\}\}';

UPDATE app_lm_outreach_enrollment
SET first_body = btrim(regexp_replace(
        regexp_replace(first_body, '\{\{\s*signature\s*(\\?\|[^{}|\n]{1,60})?\s*\}\}', '', 'g'),
        '(\r?\n[ \t]*){3,}', E'\n\n', 'g'), E' \t\r\n')
WHERE first_body ~ '\{\{\s*signature\s*(\\?\|[^{}|\n]{1,60})?\s*\}\}';
