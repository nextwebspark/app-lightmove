-- Least-privilege hardening for the LightMove application role.
--
-- Run ONCE, as a superuser, AFTER Flyway has applied the migrations (the tables must exist before
-- their ownership can be reassigned):
--
--   gcloud sql connect bright-gcc --user=postgres --database=lightmove < ops/cloudsql/harden.sql
--
-- Why this is not a Flyway migration: Flyway connects as lm_app, so lm_app owns everything it
-- creates — and a Postgres table owner can always re-grant itself a privilege it just revoked.
-- Dropping privileges from yourself is theatre. It has to come from a role that outranks you.

\set app_role 'lm_app'

-- 1. Cloud SQL grants every gcloud-created user cloudsqlsuperuser, which is far more than an
--    application needs. Take it back.
REVOKE cloudsqlsuperuser FROM lm_app;

-- 2. The audit trail becomes genuinely append-only: owned by postgres, and lm_app may only add rows
--    and read them back. Even a full SQL-injection foothold in the app cannot erase its own tracks.
ALTER TABLE app_lm_audit_event OWNER TO postgres;
ALTER SEQUENCE app_lm_audit_event_id_seq OWNER TO postgres;

REVOKE ALL     ON app_lm_audit_event        FROM lm_app;
GRANT  INSERT, SELECT ON app_lm_audit_event TO   lm_app;
GRANT  USAGE   ON SEQUENCE app_lm_audit_event_id_seq TO lm_app;

-- 3. No CREATE on the public schema: the app reads and writes, migrations are what change shape.
--    (Flyway needs this back during a deploy — see the note at the foot of this file.)
REVOKE CREATE ON SCHEMA public FROM lm_app;

-- 4. Nobody but the owner should reach this database.
REVOKE ALL ON DATABASE lightmove FROM PUBLIC;
GRANT CONNECT ON DATABASE lightmove TO lm_app;

-- ─────────────────────────────────────────────────────────────────────────────
-- Deploy note
--
-- Step 3 means lm_app can no longer run Flyway. That is deliberate: the role your application uses
-- at runtime should not be able to reshape the schema. Give migrations their own role —
--
--   CREATE ROLE lm_migrate LOGIN PASSWORD '...';
--   GRANT CREATE ON SCHEMA public TO lm_migrate;
--
-- — point Flyway at lm_migrate in CI, and leave lm_app for the running service. Until that split
-- exists, skip step 3 or the next `mvnw spring-boot:run` will fail at startup.
-- ─────────────────────────────────────────────────────────────────────────────
