-- A status is out of process by its own flag, wherever its stage sits: whoever holds one is Inactive on the board,
-- never approached again on that position and never moved by an automation. Every stage is now alike; the closed
-- stage was the one way to say this before, so its statuses are the ones backfilled flagged, and none of them stays
-- a default, since a stage's default is where a card dropped on it lands.
ALTER TABLE app_lm_workspace_candidate_status
    ADD COLUMN out_of_process boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN app_lm_workspace_candidate_status.out_of_process IS
    'Whoever holds this status has left the process: Inactive on the board, skipped by outreach.';

-- kind is frozen: nothing reads it, and its values are left as they stand so a revision still reading it during the
-- deploy keeps treating a closed stage as closed. A stage is written ACTIVE from here; a later cleanup drops it.
ALTER TABLE app_lm_workspace_candidate_stage
    ALTER COLUMN kind SET DEFAULT 'ACTIVE',
    DROP CONSTRAINT app_lm_workspace_candidate_stage_closed_live_chk;

DROP INDEX app_lm_workspace_candidate_stage_closed_uk;

COMMENT ON COLUMN app_lm_workspace_candidate_stage.kind IS
    'Frozen since V163 and read by nothing: out of process is a status''s own flag. Left for a cleanup to drop.';

-- The starting catalog is the one ALAC Partners worked out on the product: five stages, each with its own
-- ways out, and no closed stage. System statuses and stages keep the wire keys the SPA, the public API and
-- MCP already speak; only their labels moved.
CREATE OR REPLACE FUNCTION app_lm_seed_candidate_pipeline(target_workspace uuid) RETURNS void
    LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM app_lm_workspace_candidate_stage WHERE workspace_id = target_workspace) THEN
        RETURN;
    END IF;

    INSERT INTO app_lm_workspace_candidate_stage (workspace_id, key, label, colour, position, system_key)
    VALUES (target_workspace, 'identified', 'Research', 'NEUTRAL', 1, 'IDENTIFIED'),
           (target_workspace, 'contacted', 'Approach', 'VIOLET', 2, 'CONTACTED'),
           (target_workspace, 'interested', 'Assessment', 'SIGNAL', 3, 'INTERESTED'),
           (target_workspace, 'qualified', 'Shortlist', 'ADJACENT', 4, 'QUALIFIED'),
           (target_workspace, 'shortlisted', 'Offer', 'VIOLET', 5, 'SHORTLISTED')
    ON CONFLICT DO NOTHING;

    INSERT INTO app_lm_workspace_candidate_status
        (workspace_id, stage_id, key, label, colour, position, is_default, system_key, out_of_process)
    SELECT target_workspace, stage.id, seed.key, seed.label, seed.colour, seed.position, seed.is_default,
           seed.system_key, seed.out_of_process
    FROM (VALUES ('identified', 'identified', 'Identified', 'NEUTRAL', 1, true, 'IDENTIFIED', false),
                 ('identified', 'outOfScope', 'Out of scope', 'NEUTRAL', 2, false, 'OUT_OF_SCOPE', true),
                 ('identified', 'offLimits', 'Off-limits', 'NEUTRAL', 3, false, 'OFF_LIMITS', true),
                 ('identified', 'source', 'Source', 'NEUTRAL', 4, false, NULL, false),
                 ('contacted', 'contacted', 'Awaiting reply', 'ACCENT', 1, true, 'CONTACTED', false),
                 ('contacted', 'engaged', 'Confirming interest', 'ACCENT', 2, false, 'ENGAGED', false),
                 ('contacted', 'callBeingArranged', 'Call being arranged', 'VIOLET', 3, false, NULL, false),
                 ('contacted', 'callArranged', 'Call arranged', 'VIOLET', 4, false, NULL, false),
                 ('contacted', 'notInterested', 'Not interested', 'VIOLET', 5, false, 'NOT_INTERESTED', true),
                 ('contacted', 'unresponsive', 'Unresponsive', 'VIOLET', 6, false, NULL, true),
                 ('interested', 'interested', 'Screening arranged', 'SIGNAL', 1, true, 'INTERESTED', false),
                 ('interested', 'screenedOut', 'Screened-out', 'SIGNAL', 2, false, NULL, true),
                 ('interested', 'interviewBeingArranged', 'Interview being arranged', 'SIGNAL', 3, false, NULL, false),
                 ('interested', 'interviewArranged', 'Interview arranged', 'SIGNAL', 4, false, NULL, false),
                 ('interested', 'notProgressed', 'Not progressed', 'SIGNAL', 5, false, NULL, true),
                 ('interested', 'recommended', 'Recommended', 'SIGNAL', 6, false, NULL, false),
                 ('interested', 'hold', 'Hold', 'SIGNAL', 7, false, NULL, false),
                 ('interested', 'withdrew', 'Withdrew', 'SIGNAL', 8, false, NULL, true),
                 ('qualified', 'presentedAwaitingFeedback', 'Presented – awaiting feedback', 'ADJACENT', 1, false, NULL, false),
                 ('qualified', 'clientInterviewBeingArranged', 'Client interview being arranged', 'ADJACENT', 2, false, NULL, false),
                 ('qualified', 'clientInterviewArranged', 'Client interview arranged', 'ADJACENT', 3, false, NULL, false),
                 ('qualified', 'shortlisted', 'Shortlisted', 'ADJACENT', 4, true, NULL, false),
                 ('qualified', 'clientRejected', 'Client rejected', 'ADJACENT', 5, false, NULL, true),
                 ('shortlisted', 'references', 'References', 'VIOLET', 1, true, NULL, false),
                 ('shortlisted', 'clientRejectedAfterReferences', 'Client rejected after references', 'VIOLET', 2, false, NULL, true),
                 ('shortlisted', 'offerExtended', 'Offer extended', 'VIOLET', 3, false, NULL, false),
                 ('shortlisted', 'declinedOffer', 'Declined offer', 'VIOLET', 4, false, NULL, true),
                 ('shortlisted', 'placed', 'Placed', 'VIOLET', 5, false, NULL, false))
             AS seed(stage_key, key, label, colour, position, is_default, system_key, out_of_process)
    JOIN app_lm_workspace_candidate_stage stage
        ON stage.workspace_id = target_workspace AND stage.key = seed.stage_key
    ON CONFLICT DO NOTHING;
END;
$$;

-- A workspace whose catalog is still V131's seed to the letter, that nobody's candidates hold and no
-- automation names, never chose it: it starts over on the new one. Anything an admin touched, or anyone
-- stands on, is left exactly as it is.
CREATE TEMPORARY TABLE v163_reseeded ON COMMIT DROP AS
WITH v131_stage (key, label, colour, kind, position, system_key) AS (
    VALUES ('identified', 'Identified', 'NEUTRAL', 'ACTIVE', 1, 'IDENTIFIED'),
           ('contacted', 'Contacted', 'ACCENT', 'ACTIVE', 2, 'CONTACTED'),
           ('interested', 'Interested', 'GREEN', 'ACTIVE', 3, 'INTERESTED'),
           ('qualified', 'Qualified', 'ADJACENT', 'ACTIVE', 4, 'QUALIFIED'),
           ('shortlisted', 'Shortlisted', 'VIOLET', 'ACTIVE', 5, 'SHORTLISTED'),
           ('declined', 'Declined', 'RED', 'CLOSED', 6, 'DECLINED')
), v131_status (stage_key, key, label, colour, position, is_default, system_key) AS (
    VALUES ('identified', 'identified', 'Identified', 'NEUTRAL', 1, true, 'IDENTIFIED'),
           ('identified', 'researching', 'Researching', 'NEUTRAL', 2, false, NULL),
           ('identified', 'readyToApproach', 'Ready to approach', 'NEUTRAL', 3, false, NULL),
           ('contacted', 'contacted', 'Contacted', 'ACCENT', 1, true, 'CONTACTED'),
           ('contacted', 'followUpSent', 'Follow-up sent', 'ACCENT', 2, false, NULL),
           ('contacted', 'engaged', 'Engaged', 'ACCENT', 3, false, 'ENGAGED'),
           ('contacted', 'callBooked', 'Call booked', 'ACCENT', 4, false, NULL),
           ('interested', 'interested', 'Interested', 'GREEN', 1, true, 'INTERESTED'),
           ('interested', 'interestedPackageGap', 'Interested – package gap', 'GREEN', 2, false, NULL),
           ('interested', 'interestedRelocation', 'Interested – relocation concern', 'GREEN', 3, false, NULL),
           ('interested', 'considering', 'Considering', 'GREEN', 4, false, NULL),
           ('qualified', 'screened', 'Screened', 'ADJACENT', 1, true, NULL),
           ('qualified', 'qualified', 'Qualified', 'ADJACENT', 2, false, NULL),
           ('qualified', 'referencesInProgress', 'References in progress', 'ADJACENT', 3, false, NULL),
           ('shortlisted', 'shortlisted', 'Shortlisted', 'VIOLET', 1, true, NULL),
           ('shortlisted', 'presentedToClient', 'Presented to client', 'VIOLET', 2, false, NULL),
           ('shortlisted', 'clientInterview', 'Client interview', 'VIOLET', 3, false, NULL),
           ('shortlisted', 'offer', 'Offer', 'VIOLET', 4, false, NULL),
           ('declined', 'notInterested', 'Not interested', 'NEUTRAL', 1, true, 'NOT_INTERESTED'),
           ('declined', 'withdrew', 'Withdrew', 'NEUTRAL', 2, false, NULL),
           ('declined', 'notAFit', 'Not a fit', 'NEUTRAL', 3, false, NULL),
           ('declined', 'rejectedByClient', 'Rejected by client', 'NEUTRAL', 4, false, NULL),
           ('declined', 'noResponse', 'No response', 'NEUTRAL', 5, false, NULL),
           ('declined', 'offLimits', 'Off-limits', 'RED', 6, false, 'OFF_LIMITS'),
           ('declined', 'outOfScope', 'Out of scope', 'SIGNAL', 7, false, 'OUT_OF_SCOPE')
)
SELECT workspace.id AS workspace_id
FROM app_lm_workspace workspace
WHERE EXISTS (SELECT 1 FROM app_lm_workspace_candidate_stage held WHERE held.workspace_id = workspace.id)
  AND NOT EXISTS (
        SELECT 1 FROM app_lm_project_candidate candidate
        JOIN app_lm_workspace_candidate_status held ON held.id = candidate.status_id
        WHERE held.workspace_id = workspace.id)
  AND NOT EXISTS (
        SELECT 1 FROM app_lm_workspace_status_automation automation WHERE automation.workspace_id = workspace.id)
  AND NOT EXISTS (
        SELECT 1 FROM app_lm_workspace_candidate_stage held
        WHERE held.workspace_id = workspace.id AND held.retired_at IS NOT NULL)
  AND NOT EXISTS (
        SELECT 1 FROM app_lm_workspace_candidate_status held
        WHERE held.workspace_id = workspace.id AND held.retired_at IS NOT NULL)
  AND NOT EXISTS (
        (SELECT held.key, held.label, held.colour, held.kind, held.position, held.system_key
         FROM app_lm_workspace_candidate_stage held WHERE held.workspace_id = workspace.id
         EXCEPT ALL SELECT * FROM v131_stage)
        UNION ALL
        (SELECT * FROM v131_stage
         EXCEPT ALL SELECT held.key, held.label, held.colour, held.kind, held.position, held.system_key
         FROM app_lm_workspace_candidate_stage held WHERE held.workspace_id = workspace.id))
  AND NOT EXISTS (
        (SELECT stage.key, held.key, held.label, held.colour, held.position, held.is_default, held.system_key
         FROM app_lm_workspace_candidate_status held
         JOIN app_lm_workspace_candidate_stage stage ON stage.id = held.stage_id
         WHERE held.workspace_id = workspace.id
         EXCEPT ALL SELECT * FROM v131_status)
        UNION ALL
        (SELECT * FROM v131_status
         EXCEPT ALL SELECT stage.key, held.key, held.label, held.colour, held.position, held.is_default,
                           held.system_key
         FROM app_lm_workspace_candidate_status held
         JOIN app_lm_workspace_candidate_stage stage ON stage.id = held.stage_id
         WHERE held.workspace_id = workspace.id));

DELETE FROM app_lm_workspace_candidate_status
WHERE workspace_id IN (SELECT workspace_id FROM v163_reseeded);

DELETE FROM app_lm_workspace_candidate_stage
WHERE workspace_id IN (SELECT workspace_id FROM v163_reseeded);

SELECT app_lm_seed_candidate_pipeline(workspace_id) FROM v163_reseeded;

-- Last, so the reseed above compared every catalog as V131 left it.
UPDATE app_lm_workspace_candidate_status status
SET out_of_process = true, is_default = false
FROM app_lm_workspace_candidate_stage stage
WHERE stage.id = status.stage_id AND stage.kind = 'CLOSED';
