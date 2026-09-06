-- The client registry keeps a company's mark and city, so a client row can render like every other
-- company in the app.
--
-- V15 snapshotted the four fields the drawer edits (name, sector, hq_country, domain) and stopped
-- there, so a universe-backed client had no logo to show and the Clients table fell back to an
-- initials tile. These two are snapshot columns on the same terms as those four: written once from
-- the resolved Apollo row, owned by the client afterwards, and never re-resolved for display — the
-- pipeline reloads app_lm_apollo_companies wholesale, so a live join would blank a client the day its
-- company stops being published.

ALTER TABLE app_lm_client
    ADD COLUMN hq_city  varchar(96),
    ADD COLUMN logo_url text;

-- The one deferred half of that write-time snapshot: clients created before this migration resolved
-- an Apollo row and simply had nowhere to put these two fields. Matching on the stored provenance id
-- is exact — it is the id fromUniverse() resolved — so this is the write that should have happened at
-- create time, not a best-effort re-match on company name. Custom records and the retired brightdata
-- vintage carry no apollo id and are left alone.
UPDATE app_lm_client client
SET hq_city  = universe.company_city,
    logo_url = universe.logo_url
FROM app_lm_apollo_companies universe
WHERE client.company_source = 'apollo'
  AND client.company_source_id = universe.apollo_account_id;
