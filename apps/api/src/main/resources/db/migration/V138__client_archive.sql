-- A business unit or client put away: out of the registry's lists and the New position picker, restorable.
-- Only one with no live position may be archived, and none is opened or restored under an archived one.
ALTER TABLE app_lm_client
    ADD COLUMN archived_at timestamptz,
    ADD COLUMN archived_by uuid REFERENCES app_lm_user (id),
    ADD CONSTRAINT app_lm_client_archived_chk CHECK ((archived_at IS NULL) = (archived_by IS NULL));
