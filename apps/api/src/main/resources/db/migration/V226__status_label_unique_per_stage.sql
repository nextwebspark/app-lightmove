-- A status's name is unique within its stage, no longer across the workspace: a way out such as Withdrew is offered
-- at each stage it can happen at, one status per stage, so the board's band and every report read where someone
-- left from the status alone. Its key stays unique per workspace: the public API, MCP and the plugin speak keys.
DROP INDEX app_lm_workspace_candidate_status_label_uk;

CREATE UNIQUE INDEX app_lm_workspace_candidate_status_stage_label_uk
    ON app_lm_workspace_candidate_status (stage_id, lower(label));

-- The starting catalog: Research's way out reads Not approached (its key and system key unchanged), and Withdrew is
-- offered at Shortlist and Offer as well as Assessment.
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
                 ('identified', 'outOfScope', 'Not approached', 'NEUTRAL', 2, false, 'OUT_OF_SCOPE', true),
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
                 ('qualified', 'withdrewShortlist', 'Withdrew', 'ADJACENT', 6, false, NULL, true),
                 ('shortlisted', 'references', 'References', 'VIOLET', 1, true, NULL, false),
                 ('shortlisted', 'clientRejectedAfterReferences', 'Client rejected after references', 'VIOLET', 2, false, NULL, true),
                 ('shortlisted', 'offerExtended', 'Offer extended', 'VIOLET', 3, false, NULL, false),
                 ('shortlisted', 'declinedOffer', 'Declined offer', 'VIOLET', 4, false, NULL, true),
                 ('shortlisted', 'placed', 'Placed', 'VIOLET', 5, false, NULL, false),
                 ('shortlisted', 'withdrewOffer', 'Withdrew', 'VIOLET', 6, false, NULL, true))
             AS seed(stage_key, key, label, colour, position, is_default, system_key, out_of_process)
    JOIN app_lm_workspace_candidate_stage stage
        ON stage.workspace_id = target_workspace AND stage.key = seed.stage_key
    ON CONFLICT DO NOTHING;
END;
$$;

-- An existing workspace gains the two only where it still runs V163's shape — its own Withdrew a live way out under
-- Assessment — and only on a live Shortlist or Offer stage holding no status of that name, under a key nobody holds.
-- A catalog an admin reshaped otherwise is left as it stands, and nothing is renamed.
INSERT INTO app_lm_workspace_candidate_status
    (workspace_id, stage_id, key, label, colour, position, is_default, system_key, out_of_process)
SELECT stage.workspace_id, stage.id, added.key, 'Withdrew', stage.colour,
       (SELECT coalesce(max(held.position), 0) + 1 FROM app_lm_workspace_candidate_status held
        WHERE held.stage_id = stage.id),
       false, NULL, true
FROM (VALUES ('QUALIFIED', 'withdrewShortlist'), ('SHORTLISTED', 'withdrewOffer')) AS added(stage_system_key, key)
JOIN app_lm_workspace_candidate_stage stage
    ON stage.system_key = added.stage_system_key AND stage.retired_at IS NULL
WHERE EXISTS (
        SELECT 1 FROM app_lm_workspace_candidate_status withdrew
        JOIN app_lm_workspace_candidate_stage assessment ON assessment.id = withdrew.stage_id
        WHERE withdrew.workspace_id = stage.workspace_id
          AND withdrew.key = 'withdrew' AND withdrew.out_of_process AND withdrew.retired_at IS NULL
          AND assessment.system_key = 'INTERESTED')
  AND NOT EXISTS (
        SELECT 1 FROM app_lm_workspace_candidate_status named
        WHERE named.stage_id = stage.id AND lower(named.label) = 'withdrew')
  AND NOT EXISTS (
        SELECT 1 FROM app_lm_workspace_candidate_status keyed
        WHERE keyed.workspace_id = stage.workspace_id AND keyed.key = added.key);
