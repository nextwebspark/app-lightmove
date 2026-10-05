-- The assistant's name lookups (ApolloCompanyQueryService.largestNamed, matchEmployer) match
-- lower(company_name) exactly, two or three queries for each of up to ten names an answer checks.
-- No index covers that expression, so each one scanned all 100k rows of the universe.
--
-- Guarded on ownership, for V74's reason: the deployed universe is owned by the pipeline's loader
-- or, after harden.sql, by postgres, and the migrating role cannot index it. There the index is
-- harden.sql's to create (step 3), and this says so rather than failing the deploy.
DO $$
BEGIN
    IF pg_get_userbyid((SELECT relowner FROM pg_class WHERE oid = 'app_lm_apollo_companies'::regclass))
            = current_user THEN
        CREATE INDEX IF NOT EXISTS app_lm_apollo_companies_name_lower_idx
            ON app_lm_apollo_companies (lower(company_name));
    ELSE
        RAISE WARNING 'app_lm_apollo_companies is not owned by %, so app_lm_apollo_companies_name_lower_idx was not created: re-run ops/cloudsql/harden.sql as postgres.', current_user;
    END IF;
END
$$;
