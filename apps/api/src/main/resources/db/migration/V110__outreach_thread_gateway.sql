-- Which gateway made a run's thread (#650): another may not read it, so a run whose sender has since moved
-- gateway stops at its next send rather than replying into a thread nobody listens to.
ALTER TABLE app_lm_outreach_enrollment
    ADD COLUMN thread_gateway varchar(16)
        CONSTRAINT app_lm_outreach_enrollment_thread_gateway_chk CHECK (thread_gateway IN ('NYLAS', 'DIRECT')),
    DROP CONSTRAINT app_lm_outreach_enrollment_stop_reason_chk,
    ADD CONSTRAINT app_lm_outreach_enrollment_stop_reason_chk
        CHECK (stop_reason IN ('MANUAL', 'DO_NOT_CONTACT', 'LEFT_THE_RUNNING', 'UNMAPPED', 'ADDRESS_REMOVED',
                               'MAILBOX_INACTIVE', 'MAILBOX_MOVED', 'BOOKING_LINK_UNAVAILABLE', 'SEND_FAILED',
                               'SEND_UNCERTAIN'));

-- A thread so far was made by the sender's mailbox as it stands, or by Nylas where it is gone.
UPDATE app_lm_outreach_enrollment e
SET thread_gateway = coalesce((SELECT c.gateway FROM app_lm_mailbox_connection c
                               WHERE c.workspace_id = e.workspace_id AND c.user_id = e.sender_user_id), 'NYLAS')
WHERE e.thread_id IS NOT NULL;
