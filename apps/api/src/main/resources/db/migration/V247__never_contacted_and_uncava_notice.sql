-- Uncava's own notice to the executives it holds data on (#1490, decisions B2 and B6): which version a person was last
-- sent, and when — written once an email carrying its link was delivered, never on a refused one.
ALTER TABLE app_lm_person
    ADD COLUMN uncava_notice_version varchar(32),
    ADD COLUMN uncava_notice_sent_at timestamptz,
    ADD CONSTRAINT app_lm_person_uncava_notice_chk
        CHECK ((uncava_notice_version IS NULL) = (uncava_notice_sent_at IS NULL));

-- The never-contacted pass (NeverContactedErasure) reads a workspace's oldest grey people first; anyone ever told,
-- restricted or marked do not contact is never its candidate.
CREATE INDEX app_lm_person_never_contacted_idx ON app_lm_person (workspace_id, created_at, id)
    WHERE privacy_notified_at IS NULL AND NOT processing_restricted AND NOT do_not_contact;
