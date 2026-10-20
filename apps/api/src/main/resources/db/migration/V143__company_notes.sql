-- A mandate's notes on one of its companies: many per company, each authored, timed, typed, pinned and soft-deleted
-- as a person's are (V96, V141, V142), and staff-only — a client seat reads none of them (D1). They replace the
-- single free-text note V32 kept on the row, which a client seat could read through the grid, the talent map and
-- the export.
--
-- Deleting the company from the mandate deletes its notes with it: they are remarks on that decision.
CREATE TABLE app_lm_company_note (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id      uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    project_id        uuid        NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,
    triage_company_id uuid        NOT NULL REFERENCES app_lm_project_triage_company (id) ON DELETE CASCADE,
    kind              varchar(16) NOT NULL
        CONSTRAINT app_lm_company_note_kind_chk CHECK (kind IN ('GENERAL', 'CALL', 'MEETING', 'EMAIL')),
    body              text        NOT NULL
        CONSTRAINT app_lm_company_note_body_chk CHECK (btrim(body) <> '' AND char_length(body) <= 10000),
    body_format       varchar(16) NOT NULL DEFAULT 'PLAIN'
        CONSTRAINT app_lm_company_note_body_format_chk CHECK (body_format IN ('PLAIN', 'MARKDOWN')),
    pinned            boolean     NOT NULL DEFAULT false,
    author_user_id    uuid        NOT NULL REFERENCES app_lm_user (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    edited_at         timestamptz,
    edited_by         uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    deleted_at        timestamptz,
    deleted_by        uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    version           bigint      NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_company_note_deleted_chk CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL)
);

COMMENT ON TABLE app_lm_company_note IS
    'Staff-only notes on one of a mandate''s companies. Tenant data: every read filters by project_id within the '
    'caller''s workspace, and none rides TriageCompanyResponse.';

CREATE TRIGGER app_lm_company_note_touch BEFORE UPDATE ON app_lm_company_note
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE INDEX app_lm_company_note_company_idx
    ON app_lm_company_note (triage_company_id, pinned DESC, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX app_lm_company_note_deleted_idx ON app_lm_company_note (deleted_at) WHERE deleted_at IS NOT NULL;

-- Each company's old note becomes its first, filed by whoever added the company and dated when the row last
-- changed — the nearest the row records to when the note was written.
INSERT INTO app_lm_company_note (workspace_id, project_id, triage_company_id, kind, body, body_format,
                                 author_user_id, created_at, updated_at)
SELECT p.workspace_id, t.project_id, t.id, 'GENERAL', btrim(t.note), 'PLAIN', t.added_by, t.updated_at, t.updated_at
FROM app_lm_project_triage_company t
JOIN app_lm_project p ON p.id = t.project_id
WHERE btrim(coalesce(t.note, '')) <> ''
  AND char_length(btrim(t.note)) <= 10000;

COMMENT ON COLUMN app_lm_project_triage_company.note IS
    'Frozen since V143, which copied every note into app_lm_company_note; nothing reads or writes it.';
