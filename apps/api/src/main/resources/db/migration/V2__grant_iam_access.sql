-- Grants a Cloud SQL IAM principal read access to the schema, so a human can query the database with
-- their Google identity — `gcloud sql connect`, Cloud SQL Studio, or any IDE using IAM auth — without
-- anyone having to pass around the lm_app password.
--
-- Driven by the Flyway placeholder `iam_user` (spring.flyway.placeholders.iam_user, from $DB_IAM_USER).
-- Left empty, this migration does nothing at all, which is what a production deployment wants: the
-- grant is a convenience for development, not a standing entitlement.
--
-- READ ONLY, deliberately. A person poking at the database interactively should be able to look at
-- anything and change nothing. Writes go through the application, where they are validated and audited.

DO $$
DECLARE
    principal text := NULLIF('${iam_user}', '');
BEGIN
    IF principal IS NULL THEN
        RAISE NOTICE 'No iam_user placeholder set — skipping IAM grants.';
        RETURN;
    END IF;

    -- The role exists only once the principal has been added to the *instance*
    -- (`gcloud sql users create <email> --type=cloud_iam_user`). If it has not been, say so clearly
    -- rather than failing the migration and blocking startup over a developer convenience.
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = principal) THEN
        RAISE NOTICE 'IAM role % does not exist on this instance — skipping grants.', principal;
        RETURN;
    END IF;

    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), principal);
    EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', principal);
    EXECUTE format('GRANT SELECT ON ALL TABLES IN SCHEMA public TO %I', principal);
    EXECUTE format('GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO %I', principal);

    -- Without this, every table added by a future migration would be invisible to them until someone
    -- remembered to re-run a grant.
    EXECUTE format(
        'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO %I', principal);

    RAISE NOTICE 'Granted read access on % to %', current_database(), principal;
END;
$$;
