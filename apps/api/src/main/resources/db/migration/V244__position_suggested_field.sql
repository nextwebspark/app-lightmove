-- #1452: what a document reading filled on a position's brief, kept until the brief is published, when each field
-- is counted once against the prompt version that suggested it: kept if it still holds the suggestion, changed if a
-- person edited, cleared or undid it (PositionSuggestions). Keys and counts only, never a value.
--
-- field_key is a scalar's brief key (PositionFieldKeys) or a list's name; for a list, document_items is how many of
-- its rows the reading left, and it is kept only while that many still carry the document as their source.

CREATE TABLE app_lm_position_suggested_field (
    position_id    uuid        NOT NULL REFERENCES app_lm_position (id) ON DELETE CASCADE,
    field_key      varchar(64) NOT NULL,
    prompt_id      varchar(64) NOT NULL,
    version        integer     NOT NULL CHECK (version > 0),
    document_items integer     CHECK (document_items > 0),
    suggested_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (position_id, field_key)
);

-- V190's rule for a child with no workspace of its own: visible exactly where its position is.
ALTER TABLE app_lm_position_suggested_field ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_position_suggested_field FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_position_suggested_field
    USING (EXISTS (SELECT 1 FROM app_lm_position parent WHERE parent.id = app_lm_position_suggested_field.position_id))
    WITH CHECK (EXISTS (SELECT 1 FROM app_lm_position parent WHERE parent.id = app_lm_position_suggested_field.position_id));
