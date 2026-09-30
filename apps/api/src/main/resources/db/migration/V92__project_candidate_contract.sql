-- The contract half of V91 (docs/candidate-crm.md, Phase 2), and the person's profile slug stored.
--
-- V91 moved the human onto app_lm_person but left the person's columns on app_lm_project_candidate,
-- and V54's and V44's tables beside V91's copies, for the revision still serving while Flyway ran. No
-- revision reads them now, so they go. This must never ship in the deploy that carried V91.
--
-- The slug is what identifies a person across mandates. PersonMatcher used to find it by scanning
-- every LinkedIn URL of the workspace with LIKE on each filing; stored, it is one indexed equality,
-- and the unique index turns two doors racing to found one profile into a conflict rather than two
-- people.

ALTER TABLE app_lm_person ADD COLUMN profile_slug text;

-- LinkedInUrls.profileSlugOrNull, in SQL: an http(s)-shaped URL on linkedin.com or a subdomain, its
-- path percent-decoded — java.net.URI.getPath() decodes, so /in/j%C3%A9r%C3%B4me is the slug jérôme —
-- starting /in/<slug>, lower-cased. What URI.create would refuse (a space, a stray %, an unwise
-- character) names no profile. One row at a time, because a decoding that is not UTF-8 raises.
DO $$
DECLARE
    person  record;
    parts   text[];
    path    text;
    decoded text;
BEGIN
    FOR person IN
        SELECT id, regexp_replace(linkedin_url, '^[\x01-\x20]+|[\x01-\x20]+$', '', 'g') AS url
        FROM app_lm_person
        WHERE linkedin_url ~* 'linkedin\.com'
    LOOP
        CONTINUE WHEN person.url ~ '[\x01-\x20"<>\\^`{|}]';
        parts := regexp_match(person.url,
                              '^[A-Za-z][A-Za-z0-9+.-]*://(?:[^/?#@]*@)?([^/?#:]*)(?::[0-9]*)?([^?#]*)');
        CONTINUE WHEN parts IS NULL
            OR NOT (lower(parts[1]) = 'linkedin.com' OR lower(parts[1]) LIKE '%.linkedin.com');
        path := parts[2];
        CONTINUE WHEN path !~ '^/in/' OR path ~ '%($|[^0-9A-Fa-f]|[0-9A-Fa-f]($|[^0-9A-Fa-f]))';
        BEGIN
            SELECT convert_from(string_agg(CASE WHEN piece.token[1] LIKE '\%%'
                                                THEN decode(substr(piece.token[1], 2), 'hex')
                                                ELSE convert_to(piece.token[1], 'UTF8') END,
                                           ''::bytea ORDER BY piece.position), 'UTF8')
            INTO decoded
            FROM regexp_matches(path, '%[0-9A-Fa-f]{2}|[^%]+', 'g') WITH ORDINALITY AS piece(token, position);
        EXCEPTION
            WHEN character_not_in_repertoire OR untranslatable_character THEN
                CONTINUE;
        END;
        UPDATE app_lm_person SET profile_slug = lower(substring(decoded FROM '^/in/([^/?#]+)'))
        WHERE id = person.id;
    END LOOP;
END $$;

-- A profile two people of one workspace already hold stays with the older, which is who PersonMatcher
-- has always answered for it. The younger keeps its URL and loses only the key, until the merge tool
-- folds the two.
DO $$
DECLARE
    released integer;
BEGIN
    WITH ranked AS (
        SELECT id, row_number() OVER (PARTITION BY workspace_id, profile_slug ORDER BY created_at, id) AS rank
        FROM app_lm_person
        WHERE profile_slug IS NOT NULL
    )
    UPDATE app_lm_person p SET profile_slug = NULL
    FROM ranked r
    WHERE p.id = r.id AND r.rank > 1;
    GET DIAGNOSTICS released = ROW_COUNT;
    RAISE NOTICE 'V92: % person(s) share a profile with an older person of their workspace; their slug is left null', released;
END $$;

CREATE UNIQUE INDEX app_lm_person_profile_slug_uk ON app_lm_person (workspace_id, profile_slug)
    WHERE profile_slug IS NOT NULL;

COMMENT ON COLUMN app_lm_person.profile_slug IS
    'LinkedInUrls.profileSlugOrNull(linkedin_url), written with the URL: the key a filing finds the person by.';

ALTER TABLE app_lm_project_candidate
    DROP COLUMN full_name,
    DROP COLUMN title,
    DROP COLUMN seniority_level,
    DROP COLUMN linkedin_url,
    DROP COLUMN location_country,
    DROP COLUMN location_city,
    DROP COLUMN nationality,
    DROP COLUMN gender,
    DROP COLUMN years_experience,
    DROP COLUMN summary,
    DROP COLUMN compensation_currency,
    DROP COLUMN base_salary,
    DROP COLUMN bonus,
    DROP COLUMN allowances,
    DROP COLUMN long_term_incentive,
    DROP COLUMN notice_period,
    DROP COLUMN compensation_breakdown,
    DROP COLUMN profile,
    DROP COLUMN ai_inferred_fields,
    DROP COLUMN ai_nationality_reading,
    DROP COLUMN enriched_by,
    DROP COLUMN emails_looked_up_at,
    DROP COLUMN phones_looked_up_at,
    DROP COLUMN contacts_looked_up_via;

DROP TABLE app_lm_candidate_contact;
DROP TABLE app_lm_candidate_photo;

COMMENT ON TABLE app_lm_project_candidate IS
    'One workspace person on one mandate: the mandate''s status, note, custom-column values, AI assessment against its brief, and the triaged company it maps them at.';
COMMENT ON COLUMN app_lm_project_candidate.source IS
    'Which door this mandate filed the person through: MANUAL, CSV, EXTENSION or AI_SOURCED. The person keeps the first door.';
