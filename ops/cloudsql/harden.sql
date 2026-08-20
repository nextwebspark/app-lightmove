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

-- 3. The company universe is reference data: the brightdata pipeline writes it (through
--    the pipeline), the application only reads it. Giving lm_app write access would buy nothing and
--    would let a SQL-injection foothold in the app rewrite every company a consultant sees.
--
--    app_lm_apollo_companies is the universe the product actually reads. It is loaded out of band by
--    the pipeline and is typically already owned by the human who loaded it, which is why the grant is
--    guarded: on a database where postgres does not own it, ALTER TABLE would fail and take the rest
--    of this file with it.
DO $$
BEGIN
    IF to_regclass('public.app_lm_apollo_companies') IS NOT NULL THEN
        EXECUTE 'REVOKE ALL    ON app_lm_apollo_companies FROM lm_app';
        EXECUTE 'GRANT  SELECT ON app_lm_apollo_companies TO   lm_app';
    END IF;
END $$;

--    app_lm_companies is the retired brightdata copy. Nothing has read it since the Apollo universe
--    replaced it and no script refills it — sync-companies.sh is deleted. The table is deliberately
--    left in place rather than dropped; this keeps it locked down for as long as it sits there.
DO $$
BEGIN
    IF to_regclass('public.app_lm_companies') IS NOT NULL THEN
        EXECUTE 'ALTER TABLE app_lm_companies OWNER TO postgres';
        EXECUTE 'ALTER SEQUENCE app_lm_companies_id_seq OWNER TO postgres';
        EXECUTE 'REVOKE ALL    ON app_lm_companies FROM lm_app';
        EXECUTE 'GRANT  SELECT ON app_lm_companies TO   lm_app';
    END IF;
END $$;

-- 4. No CREATE on the public schema: the app reads and writes, migrations are what change shape.
--    (Flyway needs this back during a deploy — see the note at the foot of this file.)
REVOKE CREATE ON SCHEMA public FROM lm_app;

-- 5. Nobody but the owner should reach this database.
REVOKE ALL ON DATABASE lightmove FROM PUBLIC;
GRANT CONNECT ON DATABASE lightmove TO lm_app;

-- ─────────────────────────────────────────────────────────────────────────────
-- Deploy note
--
-- Step 4 means lm_app can no longer run Flyway. That is deliberate: the role your application uses
-- at runtime should not be able to reshape the schema. Give migrations their own role —
--
--   CREATE ROLE lm_migrate LOGIN PASSWORD '...';
--   GRANT CREATE ON SCHEMA public TO lm_migrate;
--
-- — point Flyway at lm_migrate in CI, and leave lm_app for the running service. Until that split
-- exists, skip step 4 or the next `mvnw spring-boot:run` will fail at startup.
--
-- Step 5 also removes lm_app's TEMPORARY privilege on the database, which it inherited from PUBLIC.
-- Nothing in the application wants a temp table. The company-universe sync did, which is one of the
-- reasons it connected as postgres — that script is gone now, and the Apollo universe is loaded out of
-- band by the pipeline rather than by anything in this repo.
-- ─────────────────────────────────────────────────────────────────────────────
