-- What each research vendor said of a person, kept whole for as long as the person is held. The people cache
-- (app_lm_vendor_person, V87/V94) ages out after its TTL and is shared across workspaces; this row is the
-- workspace's own copy, taken at filing or refresh, so a field shown later never has to be bought again.
-- One row per person per vendor: the latest answer replaces the one before it. `details` is the typed reading of
-- `record` at write time (headline, certifications, awards, posts, ...); `record` stays as sent, other people's
-- names in it (people also viewed, recommenders) included. Staff-only, never on a client seat's response.
CREATE TABLE app_lm_person_vendor_record (
    person_id      uuid        NOT NULL REFERENCES app_lm_person (id) ON DELETE CASCADE,
    workspace_id   uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    provider       varchar(32) NOT NULL
        CONSTRAINT app_lm_person_vendor_record_provider_chk
            CHECK (provider IN ('brightdata', 'brightdata-business', 'contactout', 'harvestapi')),
    record         jsonb       NOT NULL,
    details        jsonb       NOT NULL DEFAULT '{}'::jsonb,
    -- When the vendor itself last read the profile (its own timestamp), where it says; null where it does not.
    vendor_seen_at timestamptz,
    fetched_at     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (person_id, provider)
);

COMMENT ON TABLE app_lm_person_vendor_record IS
    'Each research vendor''s latest answer for a workspace person, kept while the person is held. Staff-only tenant data.';

CREATE INDEX app_lm_person_vendor_record_workspace_idx ON app_lm_person_vendor_record (workspace_id);

ALTER TABLE app_lm_person_vendor_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_person_vendor_record FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_person_vendor_record
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));
