-- A person's documents — CV, cover letter, references — shared by every mandate that maps them and
-- staff-only, like the notes (decision D1).
--
-- A document is the card a researcher sees; each file uploaded to it is a version, so a CV sent again
-- next year keeps the old one beside it. The bytes live in object storage under storage_key, never in
-- a row: a CV library grows without bound, and Cloud SQL is the most expensive place to keep it.

CREATE TABLE app_lm_person_document (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    person_id       uuid         NOT NULL REFERENCES app_lm_person (id) ON DELETE CASCADE,
    category        varchar(24)  NOT NULL
        CONSTRAINT app_lm_person_document_category_chk
            CHECK (category IN ('CV', 'COVER_LETTER', 'REFERENCE', 'CERTIFICATE', 'ASSESSMENT', 'OTHER')),
    title           varchar(255) NOT NULL
        CONSTRAINT app_lm_person_document_title_chk CHECK (btrim(title) <> ''),
    -- The latest version's file name, lower-cased: a file uploaded under the same name is its next version.
    name_key        varchar(255) NOT NULL,
    primary_cv      boolean      NOT NULL DEFAULT false,
    -- The mandate it was uploaded through; its title is kept so the document outlives it.
    project_id      uuid         REFERENCES app_lm_project (id) ON DELETE SET NULL,
    project_title   text,
    created_by      uuid         NOT NULL REFERENCES app_lm_user (id),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_person_document_primary_cv_chk CHECK (NOT primary_cv OR category = 'CV')
);

COMMENT ON TABLE app_lm_person_document IS
    'Documents on a workspace person, each a stack of uploaded versions. Staff-only tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_person_document_touch BEFORE UPDATE ON app_lm_person_document
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE INDEX app_lm_person_document_person_idx ON app_lm_person_document (person_id, name_key);
CREATE UNIQUE INDEX app_lm_person_document_primary_cv_uk ON app_lm_person_document (person_id) WHERE primary_cv;

CREATE TABLE app_lm_person_document_version (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     uuid         NOT NULL REFERENCES app_lm_person_document (id) ON DELETE CASCADE,
    -- Denormalised from the document so a duplicate upload is one indexed lookup per person.
    person_id       uuid         NOT NULL REFERENCES app_lm_person (id) ON DELETE CASCADE,
    version_no      integer      NOT NULL CONSTRAINT app_lm_person_document_version_no_chk CHECK (version_no > 0),
    file_name       varchar(255) NOT NULL,
    -- What the bytes were read as, never what the browser claimed.
    content_type    varchar(120) NOT NULL,
    size_bytes      bigint       NOT NULL CONSTRAINT app_lm_person_document_version_size_chk CHECK (size_bytes > 0),
    sha256          varchar(64)  NOT NULL,
    storage_key     varchar(512) NOT NULL UNIQUE,
    uploaded_by     uuid         NOT NULL REFERENCES app_lm_user (id),
    uploaded_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_person_document_version_uk UNIQUE (document_id, version_no)
);

COMMENT ON TABLE app_lm_person_document_version IS
    'One uploaded file of a person document; the bytes are in object storage under storage_key.';

CREATE INDEX app_lm_person_document_version_sha_idx ON app_lm_person_document_version (person_id, sha256);

ALTER TABLE app_lm_person_activity DROP CONSTRAINT app_lm_person_activity_kind_chk;
ALTER TABLE app_lm_person_activity
    ADD CONSTRAINT app_lm_person_activity_kind_chk
        CHECK (kind IN ('ADDED_TO_POOL', 'MAPPED', 'UNMAPPED', 'STATUS_CHANGED', 'PROFILE_EDITED',
                        'CONTACTS_EDITED', 'CONTACT_FOUND', 'RESEARCHED', 'AI_ASSESSED',
                        'NOTE_ADDED', 'NOTE_EDITED', 'NOTE_REMOVED',
                        'TAGGED', 'UNTAGGED', 'OWNER_CHANGED', 'DO_NOT_CONTACT_SET', 'DO_NOT_CONTACT_CLEARED',
                        'OUTREACH_ENROLLED', 'EMAIL_SENT', 'EMAIL_REPLIED', 'OUTREACH_STOPPED',
                        'MEETING_BOOKED',
                        'DOCUMENT_ADDED', 'DOCUMENT_VERSION_ADDED', 'DOCUMENT_REMOVED',
                        'DOCUMENT_VERSION_REMOVED'));
