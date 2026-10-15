-- A position put away (#archive): hidden from every list, picker and count, its outreach stopped, restorable.
-- Nothing is deleted and nothing is frozen; who archived it and when are the row's, the rest the audit trail's.
ALTER TABLE app_lm_project
    ADD COLUMN archived_at timestamptz,
    ADD COLUMN archived_by uuid REFERENCES app_lm_user (id),
    ADD CONSTRAINT app_lm_project_archived_chk CHECK ((archived_at IS NULL) = (archived_by IS NULL));

-- Archiving is the lead's call alone, and its own action: widening PROJECT_EDIT one day must not widen it
-- (V22's reasoning). Unlike every other project action, a workspace admin does not hold it without a seat.
INSERT INTO app_lm_action (scope, name, description)
VALUES ('PROJECT', 'PROJECT_ARCHIVE', 'Archive or restore the position');

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM app_lm_role r
JOIN app_lm_action a ON a.scope = 'PROJECT' AND a.name = 'PROJECT_ARCHIVE'
WHERE r.scope = 'PROJECT' AND r.name = 'LEAD';

ALTER TABLE app_lm_outreach_enrollment
    DROP CONSTRAINT app_lm_outreach_enrollment_stop_reason_chk,
    ADD CONSTRAINT app_lm_outreach_enrollment_stop_reason_chk
        CHECK (stop_reason IN ('MANUAL', 'DO_NOT_CONTACT', 'LEFT_THE_RUNNING', 'UNMAPPED', 'ADDRESS_REMOVED',
                               'MAILBOX_INACTIVE', 'MAILBOX_MOVED', 'BOOKING_LINK_UNAVAILABLE', 'SEND_FAILED',
                               'SEND_UNCERTAIN', 'POSITION_ARCHIVED'));
