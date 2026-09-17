-- Every email and phone a mandate knows for an executive, one row each, whatever door it came through.
--
-- Until now a contact lived in one of two places depending on who supplied it: the row's email and
-- phone columns (typed in the drawer, imported from a spreadsheet, or read off a profile page by the
-- plugin) or a jsonb bag under profile.contacts (what a ContactOut lookup found). Nothing recorded
-- which door a value came through, a typed address and a found one could not be told apart, and a
-- phone number was a bare string with no room for anything the provider said about it.
--
-- This is the V39 owned-list idiom: a child table the aggregate replaces from its own writes, read
-- with the row, never queried apart from it today — but a relation, so the day outreach asks "who has
-- a verified work address" it is one WHERE clause rather than a jsonb walk.
--
-- The email and phone columns on app_lm_project_candidate stay. They are the one address and one
-- number the grid shows and an export carries; this table is everything known. A lookup still
-- promotes into an empty column only — vendor data never outranks a researcher (V36's rule for
-- enrichment) — and the drawer's pencil is how the column is changed.
--
-- A miss is not a row. "Asked and found nothing" is a fact about the channel, not about any value,
-- so it is a timestamp on the candidate: null means never asked, set with no rows means the
-- provider had nothing, and asking again would spend a credit to be told the same thing.
--
-- value_key is the identity: providers spell one phone number three ways (E.164, national, dashed)
-- and an address in any case, so the primary key is the lower-cased address or the digits of the
-- number, and the spelling that arrived first is what is stored.
--
-- source is a CHECK like V47's, not a free label: a new door is a migration, and a row claiming a
-- door that does not exist would be a bug the reader could not see.

CREATE TABLE app_lm_candidate_contact (
    candidate_id  uuid         NOT NULL REFERENCES app_lm_project_candidate (id) ON DELETE CASCADE,
    channel       varchar(8)   NOT NULL,
    value         text         NOT NULL,
    value_key     text         NOT NULL,
    -- Only ever what the provider said: an address it listed as work or personal. Null for one it
    -- listed without saying, and for anything typed — the application never guesses.
    kind          varchar(16),
    verified      boolean      NOT NULL DEFAULT false,
    -- The provider's own word for the address ("Verified"), kept beside the boolean so a status we
    -- have not seen before still reaches the screen.
    status        text,
    source        varchar(16)  NOT NULL,
    found_at      timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (candidate_id, channel, value_key),
    CONSTRAINT app_lm_candidate_contact_channel_chk CHECK (channel IN ('EMAIL', 'PHONE')),
    CONSTRAINT app_lm_candidate_contact_kind_chk    CHECK (kind IS NULL OR kind IN ('WORK', 'PERSONAL')),
    CONSTRAINT app_lm_candidate_contact_source_chk
        CHECK (source IN ('MANUAL', 'CSV', 'EXTENSION', 'CONTACTOUT'))
);

COMMENT ON TABLE app_lm_candidate_contact IS
    'Every email and phone known for an executive, one row each, with the door it came through. A miss is a timestamp on the candidate, not a row here.';

ALTER TABLE app_lm_project_candidate
    ADD COLUMN emails_looked_up_at    timestamptz,
    ADD COLUMN phones_looked_up_at    timestamptz,
    ADD COLUMN contacts_looked_up_via varchar(24);

COMMENT ON COLUMN app_lm_project_candidate.emails_looked_up_at IS
    'When a contact lookup last asked for emails. Null: never asked. Set with no EMAIL rows from that provider: asked, nothing found, do not ask again.';
COMMENT ON COLUMN app_lm_project_candidate.phones_looked_up_at IS
    'The phone half of emails_looked_up_at.';
COMMENT ON COLUMN app_lm_project_candidate.contacts_looked_up_via IS
    'Which provider answered the lookups, for the drawer''s "Found via" line.';

-- ── Carry what the jsonb bag held across ─────────────────────────────────────

UPDATE app_lm_project_candidate
SET emails_looked_up_at    = NULLIF(profile #>> '{contacts,emailsLookedUpAt}', '')::timestamptz,
    phones_looked_up_at    = NULLIF(profile #>> '{contacts,phonesLookedUpAt}', '')::timestamptz,
    contacts_looked_up_via = NULLIF(profile #>> '{contacts,source}', '')
WHERE profile ? 'contacts';

-- Found values first, so an address the researcher also typed keeps the pills the provider gave it.
INSERT INTO app_lm_candidate_contact
    (candidate_id, channel, value, value_key, kind, verified, status, source, found_at)
SELECT c.id,
       'EMAIL',
       found ->> 'address',
       lower(found ->> 'address'),
       CASE upper(found ->> 'kind') WHEN 'WORK' THEN 'WORK' WHEN 'PERSONAL' THEN 'PERSONAL' END,
       lower(coalesce(found ->> 'status', '')) = 'verified',
       NULLIF(found ->> 'status', ''),
       'CONTACTOUT',
       coalesce(c.emails_looked_up_at, c.updated_at)
FROM app_lm_project_candidate c,
     jsonb_array_elements(c.profile #> '{contacts,emails}') AS found
WHERE jsonb_typeof(c.profile #> '{contacts,emails}') = 'array'
  AND NULLIF(found ->> 'address', '') IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO app_lm_candidate_contact
    (candidate_id, channel, value, value_key, kind, verified, status, source, found_at)
SELECT c.id,
       'PHONE',
       trim(found),
       regexp_replace(found, '\D', '', 'g'),
       NULL,
       false,
       NULL,
       'CONTACTOUT',
       coalesce(c.phones_looked_up_at, c.updated_at)
FROM app_lm_project_candidate c,
     jsonb_array_elements_text(c.profile #> '{contacts,phones}') AS found
WHERE jsonb_typeof(c.profile #> '{contacts,phones}') = 'array'
  AND regexp_replace(found, '\D', '', 'g') <> ''
ON CONFLICT DO NOTHING;

-- Then the row's own columns, through the door the row itself came through.
INSERT INTO app_lm_candidate_contact
    (candidate_id, channel, value, value_key, kind, verified, status, source, found_at)
SELECT id, 'EMAIL', email, lower(email), NULL, false, NULL, source, created_at
FROM app_lm_project_candidate
WHERE NULLIF(email, '') IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO app_lm_candidate_contact
    (candidate_id, channel, value, value_key, kind, verified, status, source, found_at)
SELECT id, 'PHONE', trim(phone), regexp_replace(phone, '\D', '', 'g'), NULL, false, NULL, source, created_at
FROM app_lm_project_candidate
WHERE regexp_replace(coalesce(phone, ''), '\D', '', 'g') <> ''
ON CONFLICT DO NOTHING;

UPDATE app_lm_project_candidate
SET profile = profile - 'contacts'
WHERE profile ? 'contacts';
