-- The other half of a talent map: the people.
--
-- Everything the product stores so far is companies. app_lm_project_triage_company records what a
-- mandate decided about a company; nothing anywhere can hold the executive sitting in the seat, which
-- is the thing a search mandate is actually for. This table is that row.
--
-- The mapping is two-tier and only the first tier is mandatory. A candidate belongs to a PROJECT —
-- that is the mandate they were researched for, and researching the same person for two mandates is
-- two rows, because the notes, the status and the compensation reading are all mandate-specific. The
-- second tier, the company, is optional: most executives are found at a company already in the
-- mandate's universe and are mapped to that row, but a researcher also meets people at companies the
-- universe does not carry, and refusing to store them until their employer is triaged would push that
-- name into a spreadsheet.
--
-- Hence ON DELETE SET NULL rather than CASCADE on triage_company_id, paired with the company_name
-- snapshot. Removing a company from a mandate drops the mandate's decision about the company (see
-- V32) — it must not silently delete the people mapped at it. They fall back to being unmapped
-- project candidates and keep the employer name they were entered with.
--
-- The snapshot is also what keeps this feature off a cross-table join: a candidate's row carries the
-- employer name it needs to render, so reading candidates never has to reach into the triage table.
--
-- Only manual entry is built. `source` exists from the start anyway, for the same reason
-- TriageCompanySource does: a profile typed in by a researcher, one imported from a CSV and one read
-- off a live LinkedIn page by the browser plugin are not equally trustworthy, and adding the column
-- later would mean backfilling a provenance nobody recorded.

CREATE TABLE app_lm_project_candidate (
    id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id            uuid        NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,
    triage_company_id     uuid        REFERENCES app_lm_project_triage_company (id) ON DELETE SET NULL,
    -- The employer, snapshotted. Copied from the triage company when there is one, typed by hand when
    -- there is not, and outliving the mapping either way.
    company_name          text,

    full_name             text        NOT NULL,
    title                 text,
    -- Distance from the CEO, the axis a search brief is actually written in. Nullable: a researcher
    -- who has a name and a title has not necessarily worked out the reporting line yet.
    seniority_level       varchar(16)
        CONSTRAINT app_lm_project_candidate_seniority_chk
        CHECK (seniority_level IN ('N', 'N_MINUS_1', 'N_MINUS_2', 'N_MINUS_3')),
    status                varchar(24) NOT NULL DEFAULT 'IDENTIFIED'
        CONSTRAINT app_lm_project_candidate_status_chk
        CHECK (status IN ('IDENTIFIED', 'CONTACTED', 'ENGAGED', 'INTERESTED',
                          'NOT_INTERESTED', 'OFF_LIMITS', 'OUT_OF_SCOPE')),

    email                 text,
    phone                 text,
    linkedin_url          text,

    location_country      text,
    location_city         text,
    -- Not the same fact as location_country, and the difference decides a shortlist: visa status,
    -- language and local-market credibility all follow nationality rather than current address.
    nationality           text,

    years_experience      integer,
    summary               text,
    -- The researcher's own remark, kept apart from `summary` for the same reason a triage company's
    -- note is kept apart from Apollo's short_description: one describes the person, the other
    -- describes what this mandate thinks about them.
    note                  text,

    -- Compensation is stored in whole currency units in whatever currency it was quoted in. No
    -- conversion: a rate applied at write time is wrong by the time anyone reads it, and a benchmark
    -- quoted in AED means something a USD figure does not.
    compensation_currency varchar(3),
    base_salary           bigint,
    bonus                 bigint,
    allowances            bigint,
    long_term_incentive   bigint,
    notice_period         text,

    -- Career history and languages: list-shaped, read whole, written whole, never queried by axis.
    -- The same argument V30 makes for the strategy filter, and the reason neither becomes a child
    -- table — four rows of career history are one fact about one person, not a relation.
    profile               jsonb       NOT NULL DEFAULT '{}'::jsonb,

    source                varchar(16) NOT NULL DEFAULT 'MANUAL'
        CONSTRAINT app_lm_project_candidate_source_chk
        CHECK (source IN ('MANUAL', 'CSV', 'EXTENSION')),
    -- The page the plugin read the profile off. Null for every other source.
    source_url            text,

    added_by              uuid        NOT NULL REFERENCES app_lm_user (id),
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    version               bigint      NOT NULL DEFAULT 0
);

COMMENT ON TABLE app_lm_project_candidate IS
    'Executives mapped for a mandate, optionally against one of its triaged companies. One row per project-person.';

COMMENT ON COLUMN app_lm_project_candidate.triage_company_id IS
    'The mandate''s company this person sits at, or NULL when their employer is not in the universe.';

COMMENT ON COLUMN app_lm_project_candidate.source IS
    'Which door the profile came through: MANUAL (typed in), CSV (bulk import), EXTENSION (browser plugin).';

-- The Companies grid's read: one page of companies, then the people at them.
CREATE INDEX app_lm_project_candidate_company_idx
    ON app_lm_project_candidate (project_id, triage_company_id);

-- ON DELETE SET NULL scans the referencing column on its own, which the composite above cannot serve.
CREATE INDEX app_lm_project_candidate_triage_company_idx
    ON app_lm_project_candidate (triage_company_id);

-- The foreign-key index V26 and V28 had to add after the fact for the earlier tables.
CREATE INDEX app_lm_project_candidate_added_by_idx
    ON app_lm_project_candidate (added_by);

-- Two duplicate guards rather than one, because "the same person twice" means different things on
-- either side of the mapping. Where the employer is known, a name repeated at that company is the
-- mistake; where it is not, the project is the only scope there is. Postgres treats NULLs as distinct,
-- so a single index over (project_id, triage_company_id, lower(full_name)) would enforce neither.
CREATE UNIQUE INDEX app_lm_project_candidate_at_company_uk
    ON app_lm_project_candidate (triage_company_id, lower(full_name))
    WHERE triage_company_id IS NOT NULL;

CREATE UNIQUE INDEX app_lm_project_candidate_unmapped_name_uk
    ON app_lm_project_candidate (project_id, lower(full_name))
    WHERE triage_company_id IS NULL;

CREATE TRIGGER app_lm_project_candidate_touch BEFORE UPDATE ON app_lm_project_candidate
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();
