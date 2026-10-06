-- Client registration for the MCP authorization server (epic #699, story #702): dynamic registration (RFC 7591) and
-- client id metadata documents, where the client_id is the https URL of the client's own metadata.

-- When a grant to this client was last consented: a registered client nobody connects is pruned after a while.
ALTER TABLE app_lm_oauth_client ADD COLUMN last_authorized_at timestamptz;

-- A metadata document's client is a cached copy of that document, fetched again once it expires; no other client has one.
ALTER TABLE app_lm_oauth_client ADD COLUMN metadata_fetched_at timestamptz;
ALTER TABLE app_lm_oauth_client ADD COLUMN metadata_expires_at timestamptz;
ALTER TABLE app_lm_oauth_client ADD CONSTRAINT app_lm_oauth_client_metadata_chk CHECK (
    (source = 'CIMD') = (metadata_fetched_at IS NOT NULL AND metadata_expires_at IS NOT NULL));
