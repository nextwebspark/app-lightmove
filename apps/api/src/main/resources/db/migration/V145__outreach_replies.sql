-- Outreach conversations, phase 2 (docs/outreach-conversations.md): a reply or a bounce is a line of the
-- conversation, and a reply keeps its words where the consultant's mailbox lets Uncava read them.

-- A row the previous-but-one revision wrote during V144's deploy carries neither; nothing writes one now.
UPDATE app_lm_outreach_message m
SET project_id     = e.project_id,
    person_id      = e.person_id,
    candidate_id   = coalesce(m.candidate_id, e.candidate_id),
    to_address     = coalesce(m.to_address, e.to_address),
    thread_gateway = coalesce(m.thread_gateway, e.thread_gateway)
FROM app_lm_outreach_enrollment e
WHERE e.id = m.enrollment_id
  AND (m.project_id IS NULL OR m.person_id IS NULL);

ALTER TABLE app_lm_outreach_message
    ALTER COLUMN project_id SET NOT NULL,
    ALTER COLUMN person_id SET NOT NULL;

-- What already happened, so the conversation keeps it once the activity lines that told it leave the read: one
-- reply per answered run and one bounce per bounced run, by who the run was sent to. A row with no provider id is
-- a placeholder; the first message a read finds of that kind in that thread completes it rather than adding one.
INSERT INTO app_lm_outreach_message (id, workspace_id, project_id, person_id, candidate_id, enrollment_id,
                                     direction, channel, kind, status, sender_user_id, from_address, thread_id,
                                     thread_gateway, occurred_at, created_at, updated_at, version)
SELECT gen_random_uuid(), e.workspace_id, e.project_id, e.person_id, e.candidate_id, e.id,
       'INBOUND', 'EMAIL', 'REPLY', 'RECEIVED', e.sender_user_id, e.to_address, e.thread_id,
       e.thread_gateway, e.replied_at, now(), now(), 0
FROM app_lm_outreach_enrollment e
WHERE e.replied_at IS NOT NULL;

INSERT INTO app_lm_outreach_message (id, workspace_id, project_id, person_id, candidate_id, enrollment_id,
                                     direction, channel, kind, status, sender_user_id, thread_id,
                                     thread_gateway, occurred_at, created_at, updated_at, version)
SELECT gen_random_uuid(), e.workspace_id, e.project_id, e.person_id, e.candidate_id, e.id,
       'INBOUND', 'EMAIL', 'BOUNCE', 'RECEIVED', e.sender_user_id, e.thread_id,
       e.thread_gateway, e.stopped_at, now(), now(), 0
FROM app_lm_outreach_enrollment e
WHERE e.status = 'BOUNCED' AND e.stopped_at IS NOT NULL;

-- Whether the consultant granted reading what is written into our threads, as their consent answered. Microsoft's
-- Mail.ReadWrite, which every direct connection asked, already does; a Google mailbox reconnects for gmail.readonly.
-- A Nylas mailbox's reach is lightmove.outreach.nylas.reads-replies, never this column.
ALTER TABLE app_lm_mailbox_connection
    ADD COLUMN reply_text_granted boolean NOT NULL DEFAULT false;

UPDATE app_lm_mailbox_connection SET reply_text_granted = true WHERE gateway = 'DIRECT' AND provider = 'microsoft';

-- A thread id is unique only within one mailbox.
CREATE INDEX app_lm_outreach_message_thread_idx
    ON app_lm_outreach_message (workspace_id, sender_user_id, thread_id) WHERE thread_id IS NOT NULL;

-- A reply the webhook and the poll both found is one row. Inbound only: what we sent never had to be unique by id.
CREATE UNIQUE INDEX app_lm_outreach_message_inbound_uk
    ON app_lm_outreach_message (workspace_id, sender_user_id, provider_message_id)
    WHERE direction = 'INBOUND' AND provider_message_id IS NOT NULL;

-- Which threads the poll listens to: every one an email went into lately.
CREATE INDEX app_lm_outreach_message_listening_idx
    ON app_lm_outreach_message (occurred_at)
    WHERE direction = 'OUTBOUND' AND status = 'SENT' AND thread_id IS NOT NULL;

COMMENT ON TABLE app_lm_outreach_message IS
    'One line of an executive''s conversation on a position: an outreach email as sent, a reply, or a bounce. A '
    'reply''s words are kept only for a thread Uncava started and a mailbox that grants reading them. Staff-only '
    'tenant data: every read filters by workspace_id.';
