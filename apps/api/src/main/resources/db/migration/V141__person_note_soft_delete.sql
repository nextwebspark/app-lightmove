-- A deleted note is kept for a while so its author can take the delete back, then purged for good
-- (lightmove.notes.purge-after). Until then it is out of every read: the entity carries the restriction, so the
-- list, the AI material, the timeline excerpt, a task's or a bell row's linked note and the door dedup never see it.
-- Its task, notification and mention rows stay as they are, so a restore puts everything back without
-- re-announcing anyone; the purge's hard delete then nulls or cascades them as V133 defines.
ALTER TABLE app_lm_person_note
    ADD COLUMN deleted_at timestamptz,
    ADD COLUMN deleted_by uuid REFERENCES app_lm_user (id) ON DELETE SET NULL,
    ADD CONSTRAINT app_lm_person_note_deleted_chk CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL);

COMMENT ON COLUMN app_lm_person_note.deleted_at IS
    'Set when the note was deleted; null while it is live. Purged for good once lightmove.notes.purge-after has passed.';

CREATE INDEX app_lm_person_note_deleted_idx ON app_lm_person_note (deleted_at) WHERE deleted_at IS NOT NULL;

ALTER TABLE app_lm_person_activity DROP CONSTRAINT app_lm_person_activity_kind_chk;
ALTER TABLE app_lm_person_activity
    ADD CONSTRAINT app_lm_person_activity_kind_chk
        CHECK (kind IN ('ADDED_TO_POOL', 'MAPPED', 'UNMAPPED', 'STATUS_CHANGED', 'PROFILE_EDITED',
                        'CONTACTS_EDITED', 'CONTACT_FOUND', 'RESEARCHED', 'AI_ASSESSED',
                        'NOTE_ADDED', 'NOTE_EDITED', 'NOTE_REMOVED', 'NOTE_RESTORED',
                        'TAGGED', 'UNTAGGED', 'OWNER_CHANGED', 'DO_NOT_CONTACT_SET', 'DO_NOT_CONTACT_CLEARED',
                        'OUTREACH_ENROLLED', 'EMAIL_SENT', 'EMAIL_REPLIED', 'OUTREACH_STOPPED',
                        'MEETING_BOOKED',
                        'DOCUMENT_ADDED', 'DOCUMENT_VERSION_ADDED', 'DOCUMENT_REMOVED',
                        'DOCUMENT_VERSION_REMOVED',
                        'NEXT_ACTION_SET', 'CLIENT_VISIBILITY_CHANGED',
                        'TASK_CREATED', 'TASK_COMPLETED', 'TASK_REOPENED', 'TASK_REASSIGNED', 'TASK_CANCELLED',
                        'ASSESSMENT_REVIEWED'));
