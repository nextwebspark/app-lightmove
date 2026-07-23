-- The source document behind a position brief — 1:1 with app_lm_position, same shape as that table's
-- own relationship to app_lm_project (V7). Uploading a PDF/Word/text position description here drives
-- the LLM auto-fill of the Position screen; the file itself is stored inline (bytea) rather than in
-- object storage, since this is a single, small document per position, not a document library.
--
-- extraction_status/extraction_error record the outcome for observability only — a failed extraction
-- never reaches this table at all (PositionService rolls the whole upload back), so in practice every
-- row here is COMPLETED. The columns exist so a later relaxation of that all-or-nothing rule (e.g.
-- keeping a failed upload visible for retry) does not need a schema change.

CREATE TABLE app_lm_position_document (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    position_id       uuid        NOT NULL UNIQUE REFERENCES app_lm_position (id) ON DELETE CASCADE,
    file_name         varchar(255) NOT NULL,
    content_type      varchar(120) NOT NULL,
    file_size         bigint      NOT NULL,
    content           bytea       NOT NULL,
    extraction_status varchar(16) NOT NULL
        CONSTRAINT app_lm_position_document_status_chk CHECK (extraction_status IN ('COMPLETED', 'FAILED')),
    extraction_error  text,
    uploaded_by       uuid        NOT NULL REFERENCES app_lm_user (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    version           bigint      NOT NULL DEFAULT 0
);

CREATE TRIGGER app_lm_position_document_touch BEFORE UPDATE ON app_lm_position_document
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();
