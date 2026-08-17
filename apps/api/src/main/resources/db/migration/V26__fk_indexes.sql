CREATE INDEX app_lm_position_locked_by_idx      ON app_lm_position (locked_by);
CREATE INDEX app_lm_invitation_invited_by_idx    ON app_lm_invitation (invited_by);
CREATE INDEX app_lm_invitation_accepted_by_idx   ON app_lm_invitation (accepted_by_user_id);
CREATE INDEX app_lm_project_member_added_by_idx  ON app_lm_project_member (added_by);
