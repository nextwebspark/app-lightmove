-- Notetaker phase 2 (#951): what a bot captured, one row per 30-second piece, uploaded through the API as it is made.
-- Rows of their own rather than a list on the recording: a piece lands while the same bot's heartbeat writes the
-- recording, and the two must not take turns at one row. The server names the key; the bot names only the piece.
CREATE TABLE app_lm_meeting_recording_media (
    recording_id  uuid         NOT NULL REFERENCES app_lm_meeting_recording (id) ON DELETE CASCADE,
    kind          varchar(8)   NOT NULL
        CONSTRAINT app_lm_meeting_recording_media_kind_chk CHECK (kind IN ('AUDIO', 'CAPTIONS')),
    sequence      integer      NOT NULL
        CONSTRAINT app_lm_meeting_recording_media_sequence_chk CHECK (sequence BETWEEN 1 AND 100000),
    storage_key   varchar(200) NOT NULL,
    size_bytes    bigint       NOT NULL,
    received_at   timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (recording_id, kind, sequence)
);

COMMENT ON TABLE app_lm_meeting_recording_media IS
    'A Notetaker bot''s captured pieces (bytes in the document bucket). Staff-only tenant data, read through its recording.';

-- V149's list of keys, which a bot handed over at the end, is replaced by the rows above.
ALTER TABLE app_lm_meeting_recording DROP COLUMN media;
