-- One person per workspace, mapped to many mandates (docs/candidate-crm.md, Phase 1).
--
-- Until now app_lm_project_candidate was both the human and the mandate's decision about them, so the
-- same executive researched for two mandates was two unrelated rows: two contact ledgers, two
-- compensation readings, two histories. From here app_lm_person is the human, owned by the workspace,
-- and a project-candidate row is that person on one mandate — its status, note, custom-column values,
-- brief-specific AI assessment and the triaged company it hangs off.
--
-- EXPAND ONLY. Flyway runs as a deploy step while the previous revision is still serving, so nothing
-- that revision reads is removed or rekeyed here: the moved columns stay on app_lm_project_candidate,
-- and app_lm_candidate_contact / app_lm_candidate_photo are copied rather than moved. A later contract
-- migration drops them once no revision reads them. What the old revision cannot do in the window is
-- insert a candidate (person_id is required). Nor is anything it writes in the window carried over: an
-- edit, contact save, lookup or enrichment landing on the old columns or the old ledger after this
-- backfill never reaches the person, and the new revision reads the person. Deploy at a quiet hour, or
-- drain the previous revision first.
--
-- The backfill folds existing rows into people on a key that identifies a human rather than describes
-- one: the LinkedIn profile slug, or a shared email address, within one workspace. A name alone never
-- folds two rows; that is what the merge tool is for. Two rows of one mandate are never folded into
-- one person, because a mandate holds a person once.

CREATE TABLE app_lm_person (
    id                     uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id           uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    full_name              text        NOT NULL,
    title                  text,
    seniority_level        varchar(16)
        CONSTRAINT app_lm_person_seniority_chk
        CHECK (seniority_level IN ('BOARD', 'C_SUITE', 'N_MINUS_1', 'N_MINUS_2', 'N_MINUS_3')),
    linkedin_url           text,
    -- The plugin read this person off that page; research and contact lookup key on its slug.
    linkedin_url_locked    boolean     NOT NULL DEFAULT false,
    location_country       text,
    location_city          text,
    nationality            text,
    gender                 varchar(16)
        CONSTRAINT app_lm_person_gender_chk CHECK (gender IN ('FEMALE', 'MALE', 'OTHER')),
    years_experience       integer,
    summary                text,
    compensation_currency  varchar(3),
    base_salary            bigint,
    bonus                  bigint,
    allowances             bigint,
    long_term_incentive    bigint,
    notice_period          text,
    compensation_breakdown jsonb       NOT NULL DEFAULT '{}'::jsonb,
    profile                jsonb       NOT NULL DEFAULT '{}'::jsonb,
    ai_inferred_fields     jsonb       NOT NULL DEFAULT '[]'::jsonb,
    ai_nationality_reading jsonb,
    enriched_by            varchar(16)
        CONSTRAINT app_lm_person_enriched_by_chk CHECK (enriched_by IN ('BRIGHTDATA', 'HARVESTAPI', 'CONTACTOUT')),
    -- The door the person first came through; each mandate's row keeps its own.
    source                 varchar(16) NOT NULL
        CONSTRAINT app_lm_person_source_chk CHECK (source IN ('MANUAL', 'CSV', 'EXTENSION', 'AI_SOURCED')),
    emails_looked_up_at    timestamptz,
    phones_looked_up_at    timestamptz,
    contacts_looked_up_via varchar(24),
    created_by             uuid        NOT NULL REFERENCES app_lm_user (id),
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    version                bigint      NOT NULL DEFAULT 0
);

COMMENT ON TABLE app_lm_person IS
    'One executive per workspace, shared by every mandate that maps them. Tenant data: every read filters by workspace_id.';

CREATE INDEX app_lm_person_workspace_idx ON app_lm_person (workspace_id);

CREATE TRIGGER app_lm_person_touch BEFORE UPDATE ON app_lm_person
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- V54's ledger, keyed on the person. Same identity (value_key) and the same vocabulary.
CREATE TABLE app_lm_person_contact (
    person_id  uuid        NOT NULL REFERENCES app_lm_person (id) ON DELETE CASCADE,
    channel    varchar(8)  NOT NULL,
    value      text        NOT NULL,
    value_key  text        NOT NULL,
    kind       varchar(16),
    verified   boolean     NOT NULL DEFAULT false,
    status     text,
    source     varchar(16) NOT NULL,
    found_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (person_id, channel, value_key),
    CONSTRAINT app_lm_person_contact_channel_chk CHECK (channel IN ('EMAIL', 'PHONE')),
    CONSTRAINT app_lm_person_contact_kind_chk    CHECK (kind IS NULL OR kind IN ('WORK', 'PERSONAL')),
    CONSTRAINT app_lm_person_contact_source_chk
        CHECK (source IN ('MANUAL', 'CSV', 'EXTENSION', 'CONTACTOUT', 'AI_SOURCED'))
);

-- Matching a new executive to someone already in the workspace asks by address.
CREATE INDEX app_lm_person_contact_key_idx ON app_lm_person_contact (channel, value_key);

CREATE TABLE app_lm_person_photo (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id     uuid         NOT NULL UNIQUE REFERENCES app_lm_person (id) ON DELETE CASCADE,
    content       bytea        NOT NULL,
    content_type  varchar(120) NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    version       bigint       NOT NULL DEFAULT 0
);

CREATE TRIGGER app_lm_person_photo_touch BEFORE UPDATE ON app_lm_person_photo
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- Who did what to a person, and when. Product history the timeline reads, written in the same
-- transaction as the change it describes — unlike app_lm_audit_event, which is the security ledger
-- and is written asynchronously. The mandate's title is snapshotted so a line outlives its mandate.
CREATE TABLE app_lm_person_activity (
    id             bigserial   PRIMARY KEY,
    workspace_id   uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    person_id      uuid        NOT NULL REFERENCES app_lm_person (id) ON DELETE CASCADE,
    project_id     uuid        REFERENCES app_lm_project (id) ON DELETE SET NULL,
    project_title  text,
    actor_user_id  uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    kind           varchar(32) NOT NULL
        CONSTRAINT app_lm_person_activity_kind_chk
        CHECK (kind IN ('ADDED_TO_POOL', 'MAPPED', 'UNMAPPED', 'STATUS_CHANGED', 'PROFILE_EDITED',
                        'CONTACTS_EDITED', 'CONTACT_FOUND', 'RESEARCHED', 'AI_ASSESSED')),
    occurred_at    timestamptz NOT NULL DEFAULT now(),
    details        jsonb       NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX app_lm_person_activity_person_idx ON app_lm_person_activity (person_id, id DESC);
CREATE INDEX app_lm_person_activity_workspace_idx ON app_lm_person_activity (workspace_id, id DESC);
CREATE INDEX app_lm_person_activity_actor_idx ON app_lm_person_activity (workspace_id, actor_user_id, id DESC);

ALTER TABLE app_lm_project_candidate ADD COLUMN person_id uuid;

DO $$
DECLARE
    changed integer;
BEGIN
    -- Every existing row, oldest first, with the profile slug LinkedInUrls.profileSlugOrNull reads:
    -- an http(s) URL on linkedin.com or a subdomain, whose path starts /in/<slug>, lower-cased.
    CREATE TEMP TABLE v91_row ON COMMIT DROP AS
    SELECT c.id,
           p.workspace_id,
           c.project_id,
           row_number() OVER (ORDER BY c.created_at, c.id) AS ord,
           substring(lower(trim(c.linkedin_url))
                     FROM '^[a-z][a-z0-9+.-]*://(?:[^/@]*@)?(?:[^/:?#]*\.)?linkedin\.com(?::[0-9]+)?/in/([^/?#]+)') AS slug
    FROM app_lm_project_candidate c
    JOIN app_lm_project p ON p.id = c.project_id;

    ALTER TABLE v91_row ADD COLUMN label bigint;
    UPDATE v91_row SET label = ord;

    -- Two rows are one person when they share a slug, or share an email address and do not name two
    -- different profiles. Only across mandates: within one, they were already two decisions.
    CREATE TEMP TABLE v91_edge ON COMMIT DROP AS
    SELECT a.ord AS a, b.ord AS b
    FROM v91_row a
    JOIN v91_row b ON b.workspace_id = a.workspace_id AND b.slug = a.slug
                  AND b.ord <> a.ord AND b.project_id <> a.project_id
    UNION
    SELECT a.ord, b.ord
    FROM app_lm_candidate_contact ka
    JOIN v91_row a ON a.id = ka.candidate_id
    JOIN app_lm_candidate_contact kb ON kb.channel = 'EMAIL' AND kb.value_key = ka.value_key
    JOIN v91_row b ON b.id = kb.candidate_id AND b.workspace_id = a.workspace_id
                  AND b.ord <> a.ord AND b.project_id <> a.project_id
    WHERE ka.channel = 'EMAIL'
      AND NOT (a.slug IS NOT NULL AND b.slug IS NOT NULL AND a.slug <> b.slug);

    -- Connected components: every row takes the oldest row it is linked to, however indirectly.
    LOOP
        UPDATE v91_row r
        SET label = n.lowest
        FROM (SELECT e.a, min(x.label) AS lowest
              FROM v91_edge e
              JOIN v91_row x ON x.ord = e.b
              GROUP BY e.a) n
        WHERE r.ord = n.a AND n.lowest < r.label;
        GET DIAGNOSTICS changed = ROW_COUNT;
        EXIT WHEN changed = 0;
    END LOOP;

    -- A chain can still reach back into one mandate twice; the younger row stands as its own person.
    UPDATE v91_row r
    SET label = r.ord
    WHERE EXISTS (SELECT 1 FROM v91_row o
                  WHERE o.label = r.label AND o.project_id = r.project_id AND o.ord < r.ord);

    -- The oldest row of each group founds the person and lends it its id. Every other field takes
    -- the oldest value anybody recorded; compensation comes whole from one row so two currencies are
    -- never mixed, and the profile prefers one research already filled.
    INSERT INTO app_lm_person (id, workspace_id, full_name, title, seniority_level, linkedin_url,
                               linkedin_url_locked, location_country, location_city, nationality,
                               gender, years_experience, summary, compensation_currency, base_salary,
                               bonus, allowances, long_term_incentive, notice_period,
                               compensation_breakdown, profile, ai_inferred_fields,
                               ai_nationality_reading, enriched_by, source, emails_looked_up_at,
                               phones_looked_up_at, contacts_looked_up_via, created_by, created_at,
                               updated_at)
    SELECT (array_agg(g.id ORDER BY g.ord))[1],
           g.workspace_id,
           (array_agg(g.full_name ORDER BY g.ord))[1],
           (array_agg(g.title ORDER BY g.ord) FILTER (WHERE g.title IS NOT NULL))[1],
           (array_agg(g.seniority_level ORDER BY g.ord) FILTER (WHERE g.seniority_level IS NOT NULL))[1],
           (array_agg(g.linkedin_url ORDER BY g.slug IS NULL, g.ord) FILTER (WHERE g.linkedin_url IS NOT NULL))[1],
           -- Locked only on the page a capture read: one profile in the group, and a capture of it.
           bool_or(g.source = 'EXTENSION' AND g.slug IS NOT NULL) AND count(DISTINCT g.slug) = 1,
           (array_agg(g.location_country ORDER BY g.ord) FILTER (WHERE g.location_country IS NOT NULL))[1],
           (array_agg(g.location_city ORDER BY g.ord) FILTER (WHERE g.location_city IS NOT NULL))[1],
           (array_agg(g.nationality ORDER BY g.ord) FILTER (WHERE g.nationality IS NOT NULL))[1],
           (array_agg(g.gender ORDER BY g.ord) FILTER (WHERE g.gender IS NOT NULL))[1],
           (array_agg(g.years_experience ORDER BY g.ord) FILTER (WHERE g.years_experience IS NOT NULL))[1],
           (array_agg(g.summary ORDER BY g.ord) FILTER (WHERE g.summary IS NOT NULL))[1],
           (array_agg(g.compensation_currency ORDER BY g.ord) FILTER (WHERE g.has_compensation))[1],
           (array_agg(g.base_salary ORDER BY g.ord) FILTER (WHERE g.has_compensation))[1],
           (array_agg(g.bonus ORDER BY g.ord) FILTER (WHERE g.has_compensation))[1],
           (array_agg(g.allowances ORDER BY g.ord) FILTER (WHERE g.has_compensation))[1],
           (array_agg(g.long_term_incentive ORDER BY g.ord) FILTER (WHERE g.has_compensation))[1],
           (array_agg(g.notice_period ORDER BY g.ord) FILTER (WHERE g.has_compensation))[1],
           coalesce((array_agg(g.compensation_breakdown ORDER BY g.ord) FILTER (WHERE g.has_compensation))[1],
                    '{}'::jsonb),
           coalesce((array_agg(g.profile ORDER BY (g.profile ->> 'enrichedAt') IS NULL, g.ord)
                     FILTER (WHERE g.profile <> '{}'::jsonb))[1], '{}'::jsonb),
           -- A value keeps its AI flag only if the row it came from had flagged it, so a model's guess
           -- never reads as a researcher's, nor a researcher's as a guess.
           to_jsonb(array_remove(ARRAY[
               CASE WHEN (array_agg(g.ai_inferred_fields ? 'nationality' ORDER BY g.ord)
                              FILTER (WHERE g.nationality IS NOT NULL))[1] THEN 'nationality' END,
               CASE WHEN (array_agg(g.ai_inferred_fields ? 'gender' ORDER BY g.ord)
                              FILTER (WHERE g.gender IS NOT NULL))[1] THEN 'gender' END,
               CASE WHEN (array_agg(g.ai_inferred_fields ? 'yearsExperience' ORDER BY g.ord)
                              FILTER (WHERE g.years_experience IS NOT NULL))[1] THEN 'yearsExperience' END,
               CASE WHEN (array_agg(g.ai_inferred_fields ? 'seniority' ORDER BY g.ord)
                              FILTER (WHERE g.seniority_level IS NOT NULL))[1] THEN 'seniority' END
           ], NULL)),
           (array_agg(g.ai_nationality_reading ORDER BY g.ord) FILTER (WHERE g.ai_nationality_reading IS NOT NULL))[1],
           (array_agg(g.enriched_by ORDER BY (g.profile ->> 'enrichedAt') IS NULL, g.ord)
                FILTER (WHERE g.enriched_by IS NOT NULL))[1],
           (array_agg(g.source ORDER BY g.ord))[1],
           max(g.emails_looked_up_at),
           max(g.phones_looked_up_at),
           (array_agg(g.contacts_looked_up_via
                      ORDER BY greatest(g.emails_looked_up_at, g.phones_looked_up_at) DESC NULLS LAST)
                FILTER (WHERE g.contacts_looked_up_via IS NOT NULL))[1],
           (array_agg(g.added_by ORDER BY g.ord))[1],
           min(g.created_at),
           max(g.updated_at)
    FROM (SELECT c.*, r.ord, r.label, r.workspace_id, r.slug,
                 (c.compensation_currency IS NOT NULL OR c.base_salary IS NOT NULL OR c.bonus IS NOT NULL
                  OR c.allowances IS NOT NULL OR c.long_term_incentive IS NOT NULL
                  OR c.notice_period IS NOT NULL) AS has_compensation
          FROM v91_row r
          JOIN app_lm_project_candidate c ON c.id = r.id) g
    GROUP BY g.label, g.workspace_id;

    UPDATE app_lm_project_candidate c
    SET person_id = f.id
    FROM v91_row r
    JOIN v91_row f ON f.ord = r.label
    WHERE c.id = r.id;

    -- A row founding a person was added to the pool; a row folded into one was mapped to it.
    INSERT INTO app_lm_person_activity (workspace_id, person_id, project_id, project_title,
                                        actor_user_id, kind, occurred_at, details)
    SELECT r.workspace_id, c.person_id, c.project_id, p.position_title, c.added_by,
           CASE WHEN r.ord = r.label THEN 'ADDED_TO_POOL' ELSE 'MAPPED' END,
           c.created_at,
           jsonb_build_object('door', c.source)
    FROM v91_row r
    JOIN app_lm_project_candidate c ON c.id = r.id
    JOIN app_lm_project p ON p.id = c.project_id
    ORDER BY c.created_at, r.ord;
END $$;

-- The ledgers, one per person. Where two folded rows held one address, the verified reading wins,
-- then the first found.
INSERT INTO app_lm_person_contact (person_id, channel, value, value_key, kind, verified, status, source, found_at)
SELECT DISTINCT ON (c.person_id, k.channel, k.value_key)
       c.person_id, k.channel, k.value, k.value_key, k.kind, k.verified, k.status, k.source, k.found_at
FROM app_lm_candidate_contact k
JOIN app_lm_project_candidate c ON c.id = k.candidate_id
ORDER BY c.person_id, k.channel, k.value_key, k.verified DESC, k.found_at, k.value;

INSERT INTO app_lm_person_photo (person_id, content, content_type, created_at, updated_at)
SELECT DISTINCT ON (c.person_id) c.person_id, ph.content, ph.content_type, ph.created_at, ph.updated_at
FROM app_lm_candidate_photo ph
JOIN app_lm_project_candidate c ON c.id = ph.candidate_id
ORDER BY c.person_id, c.created_at, c.id;

ALTER TABLE app_lm_project_candidate
    ALTER COLUMN person_id SET NOT NULL,
    ADD CONSTRAINT app_lm_project_candidate_person_fk
        FOREIGN KEY (person_id) REFERENCES app_lm_person (id) ON DELETE CASCADE,
    -- The name now lives on the person; this copy is kept for the old revision and dropped with it.
    ALTER COLUMN full_name DROP NOT NULL;

-- A mandate holds a person once. V36's name indexes go because the name now lives on the person;
-- CandidateService still refuses a second person of one name at one company within a mandate.
CREATE UNIQUE INDEX app_lm_project_candidate_person_uk ON app_lm_project_candidate (project_id, person_id);
CREATE INDEX app_lm_project_candidate_person_idx ON app_lm_project_candidate (person_id);
DROP INDEX app_lm_project_candidate_at_company_uk;
DROP INDEX app_lm_project_candidate_unmapped_name_uk;

COMMENT ON COLUMN app_lm_project_candidate.person_id IS
    'The workspace''s person this mandate maps. The mandate''s own facts — status, note, custom columns, AI assessment — stay on this row.';
