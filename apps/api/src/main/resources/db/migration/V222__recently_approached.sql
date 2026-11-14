-- Recently approached: before an email, a sequence or an add to a position, a researcher is told who on the team has
-- already reached the person, on any position, inside the workspace's window. 0 turns the warning off. The indexes
-- answer "every email to and from these people" and "their live sequences" across positions, which the per-position
-- indexes do not.
ALTER TABLE app_lm_workspace
    ADD COLUMN recently_approached_days integer NOT NULL DEFAULT 90,
    ADD CONSTRAINT app_lm_workspace_recently_approached_days_chk
        CHECK (recently_approached_days BETWEEN 0 AND 365);

CREATE INDEX app_lm_outreach_message_person_idx ON app_lm_outreach_message (workspace_id, person_id, occurred_at);

CREATE INDEX app_lm_outreach_enrollment_live_person_idx ON app_lm_outreach_enrollment (workspace_id, person_id)
    WHERE status IN ('SCHEDULED', 'ACTIVE');
