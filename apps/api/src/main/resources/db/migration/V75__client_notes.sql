-- The business unit drawer's free-text notes (claude-design/Clients.dc.html). Kept apart from
-- off_limits_note, which says who is protected rather than anything about the unit.
ALTER TABLE app_lm_client ADD COLUMN notes text;
