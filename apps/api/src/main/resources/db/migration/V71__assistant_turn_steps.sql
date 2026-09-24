-- What the assistant did while answering (a search and how many it matched, the card it prepared),
-- kept with the answer so a reopened chat still shows it.
ALTER TABLE app_lm_assistant_turn ADD COLUMN steps jsonb NOT NULL DEFAULT '[]'::jsonb;
