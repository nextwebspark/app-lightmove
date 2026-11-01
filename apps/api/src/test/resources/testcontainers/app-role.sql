-- The runtime role as production has it until #256: a login that owns the database (and so, on 16, the public
-- schema) and is no superuser, so row-level security binds it.
CREATE ROLE lm_app LOGIN PASSWORD 'lm_app' NOSUPERUSER NOBYPASSRLS;
ALTER DATABASE test OWNER TO lm_app;
