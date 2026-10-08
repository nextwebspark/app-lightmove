-- The clarifying questions an answer asked instead of answering: a turn that carries them ends there,
-- and the consultant's choices arrive as the thread's next question.
ALTER TABLE app_lm_assistant_turn ADD COLUMN questions jsonb;
