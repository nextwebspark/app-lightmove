-- What the firm is, for the assistant to tailor its research to: its business, the sectors it
-- works in, its competitors, its geographies. Edited by a workspace admin in Settings → General.
-- One jsonb document (V30's idiom): it is read and written whole, and never queried by field.
ALTER TABLE app_lm_workspace
    ADD COLUMN persona jsonb NOT NULL DEFAULT '{}'::jsonb;
