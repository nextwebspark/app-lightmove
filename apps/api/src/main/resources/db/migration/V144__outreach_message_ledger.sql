-- Outreach conversations, phase 1 (docs/outreach-conversations.md): every email Uncava sent becomes a line of
-- one executive's conversation on one position, read by person and position rather than through its run.
-- Expand-only. The columns a reply, a bounce or a one-off send will need are relaxed or added now so later
-- phases write rows, not schema; until they ship every row is an outbound sequence step, as before.

-- A scheduled one-off has no provider id yet, a bounce no subject or body, and nothing inbound is ever sent:
-- sent_at stays the daily cap's column and is set on an outbound email that went, never on anything else.
ALTER TABLE app_lm_outreach_message
    ALTER COLUMN enrollment_id DROP NOT NULL,
    ALTER COLUMN step DROP NOT NULL,
    ALTER COLUMN provider_message_id DROP NOT NULL,
    ALTER COLUMN subject DROP NOT NULL,
    ALTER COLUMN body DROP NOT NULL,
    ALTER COLUMN sent_at DROP NOT NULL;

-- The defaults are what every row so far is, so the previous revision's insert still lands during a deploy.
-- channel names the WhatsApp, SMS and call rows the conversation will one day carry; nothing writes them yet.
ALTER TABLE app_lm_outreach_message
    ADD COLUMN direction      varchar(8)   NOT NULL DEFAULT 'OUTBOUND'
        CONSTRAINT app_lm_outreach_message_direction_chk CHECK (direction IN ('OUTBOUND', 'INBOUND')),
    ADD COLUMN channel        varchar(16)  NOT NULL DEFAULT 'EMAIL'
        CONSTRAINT app_lm_outreach_message_channel_chk CHECK (channel IN ('EMAIL', 'WHATSAPP', 'SMS', 'CALL')),
    ADD COLUMN kind           varchar(16)  NOT NULL DEFAULT 'SEQUENCE_STEP'
        CONSTRAINT app_lm_outreach_message_kind_chk CHECK (kind IN ('SEQUENCE_STEP', 'MANUAL', 'REPLY', 'BOUNCE')),
    ADD COLUMN status         varchar(12)  NOT NULL DEFAULT 'SENT'
        CONSTRAINT app_lm_outreach_message_status_chk
            CHECK (status IN ('SCHEDULED', 'SENT', 'FAILED', 'CANCELLED', 'RECEIVED')),
    ADD COLUMN project_id     uuid         REFERENCES app_lm_project (id) ON DELETE CASCADE,
    ADD COLUMN person_id      uuid         REFERENCES app_lm_person (id) ON DELETE CASCADE,
    -- Unmapping someone keeps what was said to them, as the enrollment does.
    ADD COLUMN candidate_id   uuid         REFERENCES app_lm_project_candidate (id) ON DELETE SET NULL,
    ADD COLUMN from_address   varchar(320),
    ADD COLUMN to_address     varchar(320),
    ADD COLUMN thread_gateway varchar(16)
        CONSTRAINT app_lm_outreach_message_thread_gateway_chk CHECK (thread_gateway IN ('NYLAS', 'DIRECT')),
    ADD COLUMN occurred_at    timestamptz  NOT NULL DEFAULT now(),
    -- How body_text is to be read: a sequence's paragraph text, or (one-off sends) the composer's Markdown.
    ADD COLUMN body_format    varchar(16)  NOT NULL DEFAULT 'TEXT'
        CONSTRAINT app_lm_outreach_message_body_format_chk CHECK (body_format IN ('TEXT', 'MARKDOWN')),
    ADD COLUMN body_text      text
        CONSTRAINT app_lm_outreach_message_body_text_length_chk CHECK (char_length(body_text) <= 20000),
    ADD CONSTRAINT app_lm_outreach_message_sent_at_chk CHECK (status <> 'SENT' OR sent_at IS NOT NULL),
    ADD CONSTRAINT app_lm_outreach_message_inbound_unsent_chk CHECK (direction = 'OUTBOUND' OR sent_at IS NULL);

UPDATE app_lm_outreach_message m
SET project_id     = e.project_id,
    person_id      = e.person_id,
    candidate_id   = e.candidate_id,
    to_address     = e.to_address,
    thread_gateway = e.thread_gateway,
    occurred_at    = m.sent_at
FROM app_lm_outreach_enrollment e
WHERE e.id = m.enrollment_id;

UPDATE app_lm_outreach_message m
SET from_address = c.address
FROM app_lm_mailbox_connection c
WHERE c.workspace_id = m.workspace_id AND c.user_id = m.sender_user_id;

-- OutreachEmailBody wrote each paragraph as <p>, a line break as <br> and escaped the rest, so this reads the
-- words back as they were written. &amp; goes last, or "&amp;lt;" would come back as "<".
UPDATE app_lm_outreach_message
SET body_text = left(
        replace(replace(replace(replace(replace(
            regexp_replace(replace(replace(body, '</p><p>', E'\n\n'), '<br>', E'\n'), '<[^>]*>', '', 'g'),
        '&lt;', '<'), '&gt;', '>'), '&quot;', '"'), '&#39;', ''''), '&amp;', '&'),
        20000)
WHERE body IS NOT NULL;

-- project_id and person_id stay nullable for the deploy window: the previous revision inserts without them.
-- A row it sends then is still found through its enrollment, and phase 2's migration fills it and sets NOT NULL.

ALTER TABLE app_lm_outreach_message DROP CONSTRAINT app_lm_outreach_message_step_uk;
CREATE UNIQUE INDEX app_lm_outreach_message_step_uk ON app_lm_outreach_message (enrollment_id, step)
    WHERE kind = 'SEQUENCE_STEP';

-- One executive's conversation on one position, newest first.
CREATE INDEX app_lm_outreach_message_conversation_idx
    ON app_lm_outreach_message (workspace_id, project_id, person_id, occurred_at DESC);

COMMENT ON TABLE app_lm_outreach_message IS
    'One line of an executive''s conversation on a position: an outreach email as sent, and from later phases a '
    'reply''s text, a bounce or a one-off send. Staff-only tenant data: every read filters by workspace_id.';
