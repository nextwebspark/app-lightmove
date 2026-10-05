-- The assistant's exact-name lookups match lower(company_name). Guarded on ownership for V74's reason:
-- the deployed universe is not the migrating role's, so there harden.sql (step 3) creates the index.
DO $$
BEGIN
    IF pg_get_userbyid((SELECT relowner FROM pg_class WHERE oid = 'public.app_lm_apollo_companies'::regclass))
            = current_user THEN
        CREATE INDEX IF NOT EXISTS app_lm_apollo_companies_name_lower_idx
            ON public.app_lm_apollo_companies (lower(company_name));
    ELSE
        RAISE WARNING 'app_lm_apollo_companies is not owned by %, so app_lm_apollo_companies_name_lower_idx was not created: re-run ops/cloudsql/harden.sql as postgres.', current_user;
    END IF;
END
$$;
