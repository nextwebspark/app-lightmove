-- #922: an executive's gender and nationality leave over the public API and MCP only under their own scope,
-- candidates.demographics:read, never implied by candidates:read. Widens V117's and V115's lists; every existing key
-- and client keeps exactly what it had.
ALTER TABLE app_lm_api_key DROP CONSTRAINT app_lm_api_key_scopes_chk;
ALTER TABLE app_lm_api_key ADD CONSTRAINT app_lm_api_key_scopes_chk CHECK (
    jsonb_typeof(scopes) = 'array'
    AND jsonb_array_length(scopes) > 0
    AND scopes <@ '["projects:read", "companies:read", "candidates:read", "candidates.contacts:read",
                     "candidates.compensation:read", "candidates.demographics:read", "mcp:use"]'::jsonb);

ALTER TABLE app_lm_oauth_client DROP CONSTRAINT app_lm_oauth_client_scopes_chk;
ALTER TABLE app_lm_oauth_client ADD CONSTRAINT app_lm_oauth_client_scopes_chk CHECK (
    jsonb_typeof(scopes) = 'array'
    AND jsonb_array_length(scopes) > 0
    AND scopes <@ '["projects:read", "companies:read", "candidates:read", "candidates.contacts:read",
                     "candidates.compensation:read", "candidates.demographics:read"]'::jsonb);
