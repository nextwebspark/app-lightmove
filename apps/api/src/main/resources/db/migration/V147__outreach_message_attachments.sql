-- Outreach conversations, phase 5 (docs/outreach-conversations.md): a consultant's own email may carry files — one
-- of the person's documents, or a file uploaded from the composer. The bytes are in object storage under the message's
-- own storage_key, copied there when the email is scheduled, so removing the document version it came from later
-- neither changes nor breaks what was sent. No bytes are in the database (V105's rule).

CREATE TABLE app_lm_outreach_message_attachment (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id          uuid         NOT NULL REFERENCES app_lm_outreach_message (id) ON DELETE CASCADE,
    workspace_id        uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    -- The order the consultant attached them in, which is the order the email carries them.
    position            integer      NOT NULL CONSTRAINT app_lm_outreach_message_attachment_position_chk CHECK (position >= 0),
    file_name           varchar(255) NOT NULL,
    -- What the bytes were read as, never what the browser claimed.
    content_type        varchar(120) NOT NULL,
    size_bytes          bigint       NOT NULL CONSTRAINT app_lm_outreach_message_attachment_size_chk CHECK (size_bytes > 0),
    sha256              varchar(64)  NOT NULL,
    storage_key         varchar(512) NOT NULL UNIQUE,
    -- Where the file came from: a sent PERSON_DOCUMENT is audited as a document leaving, whatever became of its version.
    source              varchar(16)  NOT NULL
        CONSTRAINT app_lm_outreach_message_attachment_source_chk CHECK (source IN ('UPLOAD', 'PERSON_DOCUMENT')),
    -- The person's document version it was copied from; null for an upload, or once that version is removed.
    document_version_id uuid         REFERENCES app_lm_person_document_version (id) ON DELETE SET NULL,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_outreach_message_attachment_position_uk UNIQUE (message_id, position),
    CONSTRAINT app_lm_outreach_message_attachment_upload_chk CHECK (source = 'PERSON_DOCUMENT' OR document_version_id IS NULL)
);

COMMENT ON TABLE app_lm_outreach_message_attachment IS
    'Files sent with a consultant''s own outreach email; the bytes are in object storage under storage_key. Staff-only tenant data: every read filters by workspace_id.';

CREATE INDEX app_lm_outreach_message_attachment_version_idx ON app_lm_outreach_message_attachment (document_version_id)
    WHERE document_version_id IS NOT NULL;
