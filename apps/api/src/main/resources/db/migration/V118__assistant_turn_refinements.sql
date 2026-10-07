-- The next searches an answer offered, each with the universe it would leave, kept with the answer so a
-- reopened chat draws the same buttons. Null for an answer that ran no market search.
ALTER TABLE app_lm_assistant_turn ADD COLUMN refinements jsonb;
