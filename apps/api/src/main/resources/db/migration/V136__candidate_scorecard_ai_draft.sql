-- The AI drafts the consultant's scorecard. ai_drafted holds the keys still carrying the AI's value that nobody has
-- changed since — a criterion's panel:name match key, or report:<section> — so a later run rewrites only those and
-- empty ones, never a consultant's judgement (V78's ai_inferred_fields idiom). ai_drafted_at is the last draft run.
ALTER TABLE app_lm_candidate_scorecard
    ADD COLUMN ai_drafted jsonb NOT NULL DEFAULT '[]'::jsonb
        CONSTRAINT app_lm_candidate_scorecard_ai_drafted_chk CHECK (jsonb_typeof(ai_drafted) = 'array'),
    ADD COLUMN ai_drafted_at timestamptz;

-- The panel-level AI assessment the scorecard replaces. Left in place rather than dropped: a revision still serving
-- during a deploy selects both columns.
COMMENT ON COLUMN app_lm_project_candidate.ai_assessment IS
    'Frozen since V136: the retired panel-level AI assessment, neither read nor written. Dropped by a later cleanup.';
COMMENT ON COLUMN app_lm_project_candidate.ai_enrich_failed_at IS
    'Frozen since V136: neither read nor written. Dropped by a later cleanup.';
