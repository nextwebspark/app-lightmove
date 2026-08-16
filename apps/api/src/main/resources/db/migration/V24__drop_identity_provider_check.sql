-- Drop the provider CHECK added in V1 so the set of identity providers is owned by configuration
-- rather than by the schema. A provider is now a registration block in application yml — its id is
-- the button, the callback path and the value stored here — and a CHECK would mean a migration
-- every time an operator wires up another one. Same move as V10 and V21.
--
-- 'LOCAL' still means our own password hash; anything else names an OAuth registration id.

ALTER TABLE app_lm_user_identity DROP CONSTRAINT app_lm_user_identity_provider_chk;
