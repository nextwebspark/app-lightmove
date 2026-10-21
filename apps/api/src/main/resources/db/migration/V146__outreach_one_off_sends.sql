-- Outreach conversations, phase 3 (docs/outreach-conversations.md): a consultant writes one email to one executive —
-- a reply into their thread, or a first email — sent now or at a time they chose. It waits as a SCHEDULED line the
-- dispatcher claims as it claims a run, and a send is never retried.

ALTER TABLE app_lm_outreach_message
    -- When a scheduled line is due; the time it went is sent_at, as for every email.
    ADD COLUMN send_at       timestamptz,
    -- The dispatcher's claim, committed before the mail service is called (V101's idiom).
    ADD COLUMN sending_since timestamptz,
    -- The provider's id of the message this one answers, which keeps it in that thread.
    ADD COLUMN in_reply_to   varchar(255),
    -- Why a line never went: the reasons a run stops for that a single email can meet, and its sender's lost access.
    ADD COLUMN fail_reason   varchar(32)
        CONSTRAINT app_lm_outreach_message_fail_reason_chk CHECK (fail_reason IN ('DO_NOT_CONTACT', 'UNMAPPED',
            'ADDRESS_REMOVED', 'MAILBOX_INACTIVE', 'MAILBOX_MOVED', 'POSITION_ARCHIVED', 'SEND_FAILED',
            'SEND_UNCERTAIN', 'SENDER_WITHOUT_ACCESS')),
    ADD CONSTRAINT app_lm_outreach_message_scheduled_chk CHECK (status <> 'SCHEDULED' OR send_at IS NOT NULL),
    ADD CONSTRAINT app_lm_outreach_message_failed_chk CHECK ((status = 'FAILED') = (fail_reason IS NOT NULL)),
    ADD CONSTRAINT app_lm_outreach_message_claim_chk CHECK (sending_since IS NULL OR status = 'SCHEDULED');

-- What the dispatcher claims: due, unclaimed, scheduled lines.
CREATE INDEX app_lm_outreach_message_due_idx ON app_lm_outreach_message (send_at)
    WHERE status = 'SCHEDULED';
