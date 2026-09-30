-- Strategy's People mode: a people-first search over ContactOut's index, beside the company-first
-- search over the Apollo universe, and the sixth door a candidate can arrive through.

-- The mandate's people filter, autosaved like `filter` (V30's jsonb idiom) and kept apart from it: the
-- two modes are two questions, and switching between them must lose neither.
ALTER TABLE app_lm_strategy
    ADD COLUMN people_filter jsonb NOT NULL DEFAULT '{}'::jsonb;

-- A saved search is one of the two kinds. A people search holds its filter in `people_filter`; its
-- company `filter` is left empty rather than nullable, so every existing reader of it stays unchanged.
ALTER TABLE app_lm_strategy_search
    ADD COLUMN kind varchar(16) NOT NULL DEFAULT 'COMPANIES'
        CONSTRAINT app_lm_strategy_search_kind_chk CHECK (kind IN ('COMPANIES', 'PEOPLE')),
    ADD COLUMN people_filter jsonb;

-- A people-first search is asked of no company, so the page it answered is filed under none. The
-- key still hashes the whole question and its page, so one question's page two is its own row.
ALTER TABLE app_lm_vendor_people_search
    ALTER COLUMN company_slug DROP NOT NULL;

-- Places people are seen in, for the Location box's suggestions: LinkedIn's own spellings, metro areas
-- included, read out of records the cache already holds — `city` is the whole LinkedIn place line in
-- both vendors' records ("Dubai, United Arab Emirates"). The place alone, never the person.
CREATE INDEX app_lm_vendor_person_location_idx
    ON app_lm_vendor_person (lower(raw ->> 'city') text_pattern_ops)
    WHERE raw ->> 'city' IS NOT NULL;

-- A person filed from a people search: V86's idiom, the contact ledger widening with it because
-- ContactSource.ofDoor is exhaustive over the row doors.
ALTER TABLE app_lm_project_candidate
    DROP CONSTRAINT app_lm_project_candidate_source_chk;

ALTER TABLE app_lm_project_candidate
    ADD CONSTRAINT app_lm_project_candidate_source_chk
        CHECK (source IN ('MANUAL', 'CSV', 'EXTENSION', 'AI_SOURCED', 'PEOPLE_SEARCH'));

COMMENT ON COLUMN app_lm_project_candidate.source IS
    'Which door the profile came through: MANUAL (typed in), CSV (spreadsheet import), EXTENSION (browser plugin), AI_SOURCED (Find executives), PEOPLE_SEARCH (Strategy''s People mode).';

ALTER TABLE app_lm_candidate_contact
    DROP CONSTRAINT app_lm_candidate_contact_source_chk;

ALTER TABLE app_lm_candidate_contact
    ADD CONSTRAINT app_lm_candidate_contact_source_chk
        CHECK (source IN ('MANUAL', 'CSV', 'EXTENSION', 'CONTACTOUT', 'AI_SOURCED', 'PEOPLE_SEARCH'));

-- V91 moved the person into the workspace pool, and the pool records the door a person first came
-- through; its contact ledger widens with it for ContactSource.ofDoor's reason.
ALTER TABLE app_lm_person
    DROP CONSTRAINT app_lm_person_source_chk;

ALTER TABLE app_lm_person
    ADD CONSTRAINT app_lm_person_source_chk
        CHECK (source IN ('MANUAL', 'CSV', 'EXTENSION', 'AI_SOURCED', 'PEOPLE_SEARCH'));

ALTER TABLE app_lm_person_contact
    DROP CONSTRAINT app_lm_person_contact_source_chk;

ALTER TABLE app_lm_person_contact
    ADD CONSTRAINT app_lm_person_contact_source_chk
        CHECK (source IN ('MANUAL', 'CSV', 'EXTENSION', 'CONTACTOUT', 'AI_SOURCED', 'PEOPLE_SEARCH'));

-- The employer a people-search hit is filed under arrives through the same door, resolved against the
-- universe first (V34: only STRATEGY promises an Apollo id, so a matched one here is a bonus, not a rule).
ALTER TABLE app_lm_project_triage_company
    DROP CONSTRAINT app_lm_project_triage_company_source_chk;

ALTER TABLE app_lm_project_triage_company
    ADD CONSTRAINT app_lm_project_triage_company_source_chk
        CHECK (source IN ('STRATEGY', 'MANUAL', 'EXTENSION', 'CSV', 'ASSISTANT', 'PEOPLE_SEARCH'));
