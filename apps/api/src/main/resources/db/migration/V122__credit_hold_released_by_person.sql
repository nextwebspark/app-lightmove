-- A contact lookup keys its hold on how many of the person's holds for that action were released before (#737),
-- so it counts them on every paid press.
CREATE INDEX app_lm_credit_hold_released_person_idx
    ON app_lm_credit_hold (workspace_id, person_id, action)
    WHERE status = 'RELEASED';
