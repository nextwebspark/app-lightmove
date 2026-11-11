-- The last time an invitation's email could not be handed to the mail provider, so the Team page can say so
-- rather than listing it as sent. Cleared by the next send that goes through.
ALTER TABLE app_lm_invitation ADD COLUMN email_failed_at timestamptz;
