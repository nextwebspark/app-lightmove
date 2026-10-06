-- The MCP server (epic #699, story #703): an API key reaches it only by opting in with mcp:use, a scope that reads
-- nothing by itself. Widens V114's scope list; every existing key keeps exactly what it had.
ALTER TABLE app_lm_api_key DROP CONSTRAINT app_lm_api_key_scopes_chk;
ALTER TABLE app_lm_api_key ADD CONSTRAINT app_lm_api_key_scopes_chk CHECK (
    jsonb_typeof(scopes) = 'array'
    AND jsonb_array_length(scopes) > 0
    AND scopes <@ '["projects:read", "companies:read", "candidates:read",
                     "candidates.contacts:read", "candidates.compensation:read", "mcp:use"]'::jsonb);
