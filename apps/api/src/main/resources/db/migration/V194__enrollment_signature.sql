-- Email signatures, phase 3 (docs/email-signatures.md): the signature a run's emails carry, chosen by its sender at
-- Start and drawn there, so every step — the first email and each follow-up — closes the same way whatever becomes of
-- the signature afterwards. Null: the run sends none. Its HTML is escaped and its only pictures are Uncava's own.

ALTER TABLE app_lm_outreach_enrollment
    ADD COLUMN signature_html text;
