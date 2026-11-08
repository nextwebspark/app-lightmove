-- A workspace seeded before V163 kept V131's closed Declined stage, which V163 left a stage of ways out alone, so the
-- Pipeline board drew it a cell of its own in the Out of process band. Someone who left the search stands under the
-- stage they left it at, so the old stage is dissolved into the stages the board draws: each of its statuses moves
-- under the stage it is usually given at, and whoever left at another stage gets that status under that stage.
-- A closed stage an admin has since given a status in process is a stage like any other, and is left as it stands.
CREATE TEMPORARY TABLE v195_closed ON COMMIT DROP AS
SELECT stage.id AS stage_id, stage.workspace_id
FROM app_lm_workspace_candidate_stage stage
WHERE stage.kind = 'CLOSED'
  AND NOT EXISTS (
        SELECT 1 FROM app_lm_workspace_candidate_status status
        WHERE status.stage_id = stage.id AND NOT status.out_of_process AND status.retired_at IS NULL);

-- The board's columns: live stages holding a live status in process.
CREATE TEMPORARY TABLE v195_gate ON COMMIT DROP AS
SELECT stage.id AS stage_id, stage.workspace_id, stage.key, stage.label, stage.system_key, stage.position
FROM app_lm_workspace_candidate_stage stage
WHERE stage.workspace_id IN (SELECT workspace_id FROM v195_closed)
  AND stage.id NOT IN (SELECT stage_id FROM v195_closed)
  AND stage.retired_at IS NULL
  AND EXISTS (
        SELECT 1 FROM app_lm_workspace_candidate_status status
        WHERE status.stage_id = stage.id AND NOT status.out_of_process AND status.retired_at IS NULL);

DELETE FROM v195_closed WHERE workspace_id NOT IN (SELECT workspace_id FROM v195_gate);

-- Where each holder of a closed status left: the stage of the latest status their position's history shows them at,
-- read off its status lines by key (a key is never rewritten). Null where no line says.
CREATE TEMPORARY TABLE v195_holder ON COMMIT DROP AS
SELECT candidate.id AS candidate_id, candidate.status_id,
       (SELECT coalesce(to_gate.stage_id, from_gate.stage_id)
        FROM app_lm_person_activity line
        LEFT JOIN app_lm_workspace_candidate_status to_status
               ON to_status.workspace_id = closed.workspace_id AND to_status.key = line.details ->> 'to'
        LEFT JOIN v195_gate to_gate ON to_gate.stage_id = to_status.stage_id
        LEFT JOIN app_lm_workspace_candidate_status from_status
               ON from_status.workspace_id = closed.workspace_id AND from_status.key = line.details ->> 'from'
        LEFT JOIN v195_gate from_gate ON from_gate.stage_id = from_status.stage_id
        WHERE line.person_id = candidate.person_id
          AND line.project_id = candidate.project_id
          AND line.kind = 'STATUS_CHANGED'
          AND coalesce(to_gate.stage_id, from_gate.stage_id) IS NOT NULL
        ORDER BY line.id DESC
        LIMIT 1) AS left_at
FROM app_lm_project_candidate candidate
JOIN app_lm_workspace_candidate_status held ON held.id = candidate.status_id
JOIN v195_closed closed ON closed.stage_id = held.stage_id;

-- Each closed status's new stage: where V131's seeded ways out are usually given (V163's catalog places them the
-- same), else where most of its holders left, else the last column.
CREATE TEMPORARY TABLE v195_way_out ON COMMIT DROP AS
WITH usual (status_key, stage_system_key) AS (
    VALUES ('offLimits', 'IDENTIFIED'),
           ('outOfScope', 'IDENTIFIED'),
           ('notInterested', 'CONTACTED'),
           ('noResponse', 'CONTACTED'),
           ('withdrew', 'INTERESTED'),
           ('notAFit', 'QUALIFIED'),
           ('rejectedByClient', 'SHORTLISTED')
)
SELECT status.id AS status_id,
       coalesce(
           (SELECT gate.stage_id FROM usual
            JOIN v195_gate gate ON gate.workspace_id = status.workspace_id AND gate.system_key = usual.stage_system_key
            WHERE usual.status_key = status.key),
           (SELECT gate.stage_id FROM v195_holder holder
            JOIN v195_gate gate ON gate.stage_id = holder.left_at
            WHERE holder.status_id = status.id
            GROUP BY gate.stage_id, gate.position
            ORDER BY count(*) DESC, gate.position DESC
            LIMIT 1),
           (SELECT gate.stage_id FROM v195_gate gate
            WHERE gate.workspace_id = status.workspace_id
            ORDER BY gate.position DESC
            LIMIT 1)) AS home
FROM app_lm_workspace_candidate_status status
JOIN v195_closed closed ON closed.stage_id = status.stage_id;

UPDATE v195_holder holder
SET left_at = way_out.home
FROM v195_way_out way_out
WHERE way_out.status_id = holder.status_id AND holder.left_at IS NULL;

UPDATE app_lm_workspace_candidate_status status
SET stage_id   = moved.home,
    position   = moved.after + moved.rank,
    is_default = false,
    version    = status.version + 1
FROM (SELECT way_out.status_id, way_out.home,
             (SELECT coalesce(max(other.position), 0) FROM app_lm_workspace_candidate_status other
              WHERE other.stage_id = way_out.home) AS after,
             row_number() OVER (PARTITION BY way_out.home ORDER BY held.position, held.id) AS rank
      FROM v195_way_out way_out
      JOIN app_lm_workspace_candidate_status held ON held.id = way_out.status_id) moved
WHERE status.id = moved.status_id;

-- A status sits under one stage and its label is the workspace's own, so someone who left at another stage than
-- their status's new one gets a copy of it there, named for that stage.
DO $$
DECLARE
    needed     record;
    base_key   text;
    free_key   text;
    base_label text;
    free_label text;
    attempt    integer;
    copy_id    uuid;
BEGIN
    FOR needed IN
        SELECT DISTINCT status.id AS status_id, status.workspace_id, status.key, status.label, status.colour,
                        status.retired_at, status.position, gate.stage_id, gate.key AS gate_key,
                        gate.label AS gate_label, gate.position AS gate_position
        FROM v195_holder holder
        JOIN v195_way_out way_out ON way_out.status_id = holder.status_id
        JOIN app_lm_workspace_candidate_status status ON status.id = holder.status_id
        JOIN v195_gate gate ON gate.stage_id = holder.left_at
        WHERE holder.left_at <> way_out.home
        ORDER BY status.workspace_id, gate.position, status.position, status.id
    LOOP
        base_key := left(needed.key || upper(left(needed.gate_key, 1)) || substr(needed.gate_key, 2), 40);
        free_key := base_key;
        attempt := 1;
        WHILE EXISTS (SELECT 1 FROM app_lm_workspace_candidate_status
                      WHERE workspace_id = needed.workspace_id AND key = free_key) LOOP
            attempt := attempt + 1;
            free_key := left(base_key, 40 - length(attempt::text)) || attempt;
        END LOOP;

        base_label := rtrim(left(needed.label || ' (' || needed.gate_label || ')', 40));
        free_label := base_label;
        attempt := 1;
        WHILE EXISTS (SELECT 1 FROM app_lm_workspace_candidate_status
                      WHERE workspace_id = needed.workspace_id AND lower(label) = lower(free_label)) LOOP
            attempt := attempt + 1;
            free_label := rtrim(left(base_label, 39 - length(attempt::text))) || ' ' || attempt;
        END LOOP;

        INSERT INTO app_lm_workspace_candidate_status
            (workspace_id, stage_id, key, label, colour, position, is_default, out_of_process, retired_at)
        SELECT needed.workspace_id, needed.stage_id, free_key, free_label, needed.colour,
               coalesce(max(other.position), 0) + 1, false, true, needed.retired_at
        FROM app_lm_workspace_candidate_status other
        WHERE other.stage_id = needed.stage_id
        RETURNING id INTO copy_id;

        UPDATE app_lm_project_candidate candidate
        SET status_id = copy_id,
            version   = candidate.version + 1
        FROM v195_holder holder
        WHERE holder.candidate_id = candidate.id
          AND holder.status_id = needed.status_id
          AND holder.left_at = needed.stage_id;
    END LOOP;
END;
$$;

DELETE FROM app_lm_workspace_candidate_stage stage
USING v195_closed closed
WHERE stage.id = closed.stage_id;

-- Live stages first and retired ones behind them, as every stage edit numbers them.
UPDATE app_lm_workspace_candidate_stage stage
SET position = renumbered.position,
    version  = stage.version + 1
FROM (SELECT id, row_number() OVER (PARTITION BY workspace_id
                                    ORDER BY retired_at IS NOT NULL, position, id) AS position
      FROM app_lm_workspace_candidate_stage
      WHERE workspace_id IN (SELECT workspace_id FROM v195_closed)) renumbered
WHERE stage.id = renumbered.id AND stage.position <> renumbered.position;
