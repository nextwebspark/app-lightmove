-- Recently used email templates (docs/email-templates.md §D): a sender's own emails that came from a template, newest
-- first, so the picker's read is a short index scan rather than every message the sender ever wrote.
CREATE INDEX app_lm_outreach_message_template_sender_idx
    ON app_lm_outreach_message (workspace_id, sender_user_id, created_at DESC)
    WHERE template_id IS NOT NULL;
