-- The references V133 left unindexed. Unmapping an executive, deleting a note or a task clears or removes the
-- rows pointing at it, and without an index that is a scan of the whole table each time — the notification
-- table above all, which only grows. Partial, since most rows point at nothing.

CREATE INDEX app_lm_task_candidate_ref_idx ON app_lm_task (candidate_id) WHERE candidate_id IS NOT NULL;
CREATE INDEX app_lm_task_note_ref_idx ON app_lm_task (note_id) WHERE note_id IS NOT NULL;

CREATE INDEX app_lm_notification_candidate_ref_idx ON app_lm_notification (candidate_id)
    WHERE candidate_id IS NOT NULL;
CREATE INDEX app_lm_notification_task_ref_idx ON app_lm_notification (task_id) WHERE task_id IS NOT NULL;
CREATE INDEX app_lm_notification_note_ref_idx ON app_lm_notification (note_id) WHERE note_id IS NOT NULL;
