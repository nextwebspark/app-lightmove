-- AED is the default currency: a new workspace, a new brief and the shared role templates start in
-- the UAE dirham rather than the US dollar. A currency label only — no stored figure is converted.
--
-- Existing rows follow V67's rule for what is nobody's choice yet. A brief at version 0 has never
-- been saved, so its currency is the template's and moves; a saved brief keeps what somebody chose.
-- The shared library (workspace_id IS NULL) moves; a firm's own template is that firm's choice and
-- does not. revised_at is left alone, so a firm's copy is not flagged "the library moved on" for a
-- default. A workspace's default currency is a setting nothing else reads, so every USD one moves.
ALTER TABLE app_lm_workspace ALTER COLUMN default_currency SET DEFAULT 'AED';
UPDATE app_lm_workspace SET default_currency = 'AED' WHERE default_currency = 'USD';

ALTER TABLE app_lm_position ALTER COLUMN currency SET DEFAULT 'AED';
UPDATE app_lm_position SET currency = 'AED' WHERE version = 0 AND currency = 'USD';

UPDATE app_lm_position_template
SET body = jsonb_set(body, '{currency}', '"AED"')
WHERE workspace_id IS NULL AND body ->> 'currency' = 'USD';
