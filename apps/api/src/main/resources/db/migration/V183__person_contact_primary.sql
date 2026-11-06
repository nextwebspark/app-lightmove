-- The email and the phone a person's team chose as theirs to reach them on: drawn in the drawer's header, listed first
-- everywhere, and every send's default. At most one per channel. No backfill: with none chosen, the first of the
-- ledger's order stands in (Person.listed), so every existing person already reads as having one.
ALTER TABLE app_lm_person_contact
    ADD COLUMN is_primary boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX app_lm_person_contact_primary_uk
    ON app_lm_person_contact (person_id, channel)
    WHERE is_primary;
