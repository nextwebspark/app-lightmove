-- Grants one Cloud SQL IAM principal access to this database. Driven by grant-db-user.sh, which
-- passes :principal (the Google account) and :mode ('read' or 'write').
--
-- Run as an *owner*, which today means lm_app — not postgres. On Cloud SQL the postgres role is not a
-- superuser (rolsuper = f) and is not a member of lm_app; the two are merely siblings under
-- cloudsqlsuperuser, which confers nothing between them. A non-owner cannot GRANT, so postgres would
-- fail with "must be owner of relation app_lm_user" on every table. lm_app owns them all, and is itself
-- a member of cloudsqlsuperuser — which owns the database and, through pg_database_owner, the public
-- schema — so it can also grant CONNECT and USAGE. It is the only role that can do the whole job.
--
-- This is deliberately not a Flyway migration; see the header of grant-db-user.sh.
--
-- Every grant is guarded, because harden.sql moves the ground under this file: it reassigns
-- app_lm_audit_event and app_lm_companies to postgres and strips lm_app of cloudsqlsuperuser. After it
-- runs, no single role can grant everything, so this script grants what the connected role owns and
-- tells you plainly what it could not — rather than half-succeeding in silence.

\set ON_ERROR_STOP on

-- psql does not interpolate :variables inside a dollar-quoted body, so they are carried in as GUCs.
SELECT set_config('lightmove.principal', :'principal', false);
SELECT set_config('lightmove.mode', :'mode', false);

DO $$
DECLARE
    principal text    := NULLIF(current_setting('lightmove.principal'), '');
    writable  boolean := current_setting('lightmove.mode') = 'write';

    -- Reference data, never writable by a human:
    --   app_lm_audit_event — append-only. A log its subject can rewrite is not a log.
    --   app_lm_companies   — owned by the ETL pipeline. A hand-written row survives only until the next
    --                        sync-companies.sh overwrites it.
    read_only_tables constant text[] := ARRAY['app_lm_audit_event', 'app_lm_companies'];

    rel      record;
    creator  text;
    skipped  text[] := '{}';
BEGIN
    IF principal IS NULL THEN
        RAISE EXCEPTION 'No principal given.';
    END IF;

    -- The Postgres role exists only once the principal is registered on the *instance*. Fail loudly:
    -- unlike V2 (a startup convenience that must never block a boot) a human is waiting on this, and
    -- granting nothing quietly is the worst outcome.
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = principal) THEN
        RAISE EXCEPTION 'No role % on this instance. Register it first: gcloud sql users create % --instance=<instance> --type=cloud_iam_user',
            principal, principal;
    END IF;

    -- Currently redundant — PUBLIC still holds both by default. They stop being redundant the moment
    -- harden.sql revokes ALL ON DATABASE from PUBLIC, and a role granted its tables but not CONNECT
    -- fails at the door for reasons no table grant explains.
    BEGIN
        EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), principal);
        EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', principal);
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE WARNING 'Could not grant CONNECT/USAGE as %. Re-run as a member of the database owner.',
            current_user;
    END;

    -- Table by table, granting only what the connected role owns. A blanket GRANT ON ALL TABLES would
    -- abort the whole statement on the first table it does not own, which after harden.sql is a
    -- certainty rather than a hypothetical.
    FOR rel IN
        SELECT tablename, tableowner
        FROM pg_tables
        WHERE schemaname = 'public'
        ORDER BY tablename
    LOOP
        IF NOT pg_has_role(current_user, rel.tableowner, 'USAGE') THEN
            skipped := skipped || rel.tablename;
            CONTINUE;
        END IF;

        EXECUTE format('GRANT SELECT ON public.%I TO %I', rel.tablename, principal);

        IF writable AND rel.tablename LIKE 'app\_lm\_%' AND NOT (rel.tablename = ANY (read_only_tables)) THEN
            EXECUTE format('GRANT INSERT, UPDATE, DELETE ON public.%I TO %I', rel.tablename, principal);
        END IF;
    END LOOP;

    IF writable THEN
        FOR rel IN
            SELECT sequencename, sequenceowner
            FROM pg_sequences
            WHERE schemaname = 'public'
              AND sequencename LIKE 'app\_lm\_%'
        LOOP
            CONTINUE WHEN NOT pg_has_role(current_user, rel.sequenceowner, 'USAGE');
            -- A sequence backing a read-only table is of no use to a writer, and granting it would let
            -- them burn ids in a table they cannot insert into.
            CONTINUE WHEN EXISTS (
                SELECT 1 FROM unnest(read_only_tables) t
                WHERE rel.sequencename LIKE t || '%'
            );
            EXECUTE format('GRANT USAGE, SELECT, UPDATE ON SEQUENCE public.%I TO %I',
                rel.sequencename, principal);
        END LOOP;
    END IF;

    -- Without this, every table a future migration adds is invisible until someone re-runs this script.
    -- FOR ROLE is load-bearing: the default attaches to the role that *creates* the table, not the one
    -- issuing this statement. V2 could omit it only because Flyway ran as lm_app itself.
    FOREACH creator IN ARRAY ARRAY['lm_app', 'lm_migrate'] LOOP
        CONTINUE WHEN NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = creator);
        CONTINUE WHEN NOT pg_has_role(current_user, creator, 'USAGE');

        EXECUTE format(
            'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT ON TABLES TO %I',
            creator, principal);
        EXECUTE format(
            'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT ON SEQUENCES TO %I',
            creator, principal);

        IF writable THEN
            EXECUTE format(
                'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT INSERT, UPDATE, DELETE ON TABLES TO %I',
                creator, principal);
            EXECUTE format(
                'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO %I',
                creator, principal);
        END IF;
    END LOOP;

    RAISE NOTICE '% access on % granted to %',
        CASE WHEN writable THEN 'Read/write' ELSE 'Read' END, current_database(), principal;

    IF cardinality(skipped) > 0 THEN
        RAISE WARNING 'Not granted (% does not own these): %. Re-run as their owner.',
            current_user, array_to_string(skipped, ', ');
    END IF;
END;
$$;
