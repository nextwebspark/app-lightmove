-- A firm for the assistant to know, for the LOCAL database only: `npm run dev:db:seed-firm`.
--
-- The assistant puts the workspace's company and persona (V68, V69) in every system prompt, and a
-- workspace made before signup picked its company from the universe has neither. This fills one: a
-- diversified Gulf group hiring senior leaders for its own businesses, the case where a mandate's
-- client is one of the group's own business units.
--
-- Fills only what is empty, so a persona someone typed in Settings is never overwritten. The company
-- is tied to a real universe row where the local snapshot has one, so the prompt carries a headcount.
\set ON_ERROR_STOP on
\if :{?workspace}
\else
  \set workspace ''
\endif

SELECT w.id AS workspace_id, w.name AS workspace_name
FROM app_lm_workspace w
WHERE :'workspace' = '' OR w.id = NULLIF(:'workspace', '')::uuid
ORDER BY w.created_at DESC
LIMIT 1
\gset

\if :{?workspace_id}
\else
  \echo 'seed-firm: no workspace to seed — sign up in the app first'
  \quit
\endif

BEGIN;

UPDATE app_lm_workspace w
SET apollo_account_id    = picked.apollo_account_id,
    company_industry     = coalesce(picked.industry, 'retail'),
    company_city         = coalesce(picked.company_city, 'Dubai'),
    company_country      = coalesce(picked.company_country, 'United Arab Emirates'),
    company_website      = coalesce(picked.website, 'https://www.alfuttaim.com'),
    company_linkedin_url = picked.company_linkedin_url,
    logo_url             = picked.logo_url
FROM (SELECT NULL::text AS apollo_account_id, NULL::text AS industry, NULL::text AS company_city,
             NULL::text AS company_country, NULL::text AS website, NULL::text AS company_linkedin_url,
             NULL::text AS logo_url, 1 AS rank
      UNION ALL
      SELECT a.apollo_account_id, a.industry, a.company_city, a.company_country, a.website,
             a.company_linkedin_url, a.logo_url, 0
      FROM app_lm_apollo_companies a
      WHERE lower(a.company_name) IN ('al-futtaim', 'al futtaim', 'al-futtaim group', 'al futtaim group')
        AND a.company_country = 'United Arab Emirates'
      ORDER BY 8, 1
      LIMIT 1) picked
WHERE w.id = :'workspace_id' AND w.apollo_account_id IS NULL AND w.company_industry IS NULL;

UPDATE app_lm_workspace
SET persona = jsonb_build_object(
        'summary', 'Diversified Gulf group — retail, real estate, automotive and hospitality — hiring senior leaders for its own businesses.',
        'sectors', jsonb_build_array('retail', 'real estate', 'automotive', 'hospitality'),
        'competitors', jsonb_build_array('Majid Al Futtaim', 'Landmark Group', 'Alshaya Group', 'Apparel Group'),
        'geographies', jsonb_build_array('United Arab Emirates', 'Saudi Arabia', 'Qatar', 'Kuwait'),
        'notes', 'A mandate''s client is one of the group''s own business units.')
WHERE id = :'workspace_id' AND (persona = '{}'::jsonb OR persona->>'summary' IS NULL);

COMMIT;

\echo 'seed-firm: seeded' :'workspace_name'
