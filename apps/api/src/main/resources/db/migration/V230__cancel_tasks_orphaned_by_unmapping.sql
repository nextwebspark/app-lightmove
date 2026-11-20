-- Before #1402 taking an executive off a position left their open tasks there open with no candidate, still falling
-- due in My tasks, the bell and the digest. Cancel those, and only those: a rule can file a task for someone the
-- position never mapped, which has no candidate either, so a task counts as orphaned only when an UNMAPPED line for
-- its person on its position came after it. A rule's task gives up its key as UnmappedCandidateTasks does.
UPDATE app_lm_task t
   SET status       = 'CANCELLED',
       completed_by = NULL,
       completed_at = NULL,
       rule_key     = CASE WHEN t.rule_key IS NULL THEN NULL
                           ELSE split_part(t.rule_key, ':released:', 1) || ':released:' || t.id END,
       version      = t.version + 1
 WHERE t.status = 'OPEN'
   AND t.candidate_id IS NULL
   AND EXISTS (SELECT 1 FROM app_lm_person_activity a
                WHERE a.person_id = t.person_id
                  AND a.project_id = t.project_id
                  AND a.kind = 'UNMAPPED'
                  AND a.occurred_at >= t.created_at);
