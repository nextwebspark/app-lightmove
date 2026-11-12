-- Exporting everything held on one person (#919, GDPR Art. 15 and 20): an admin's action.
--
-- Its own action rather than PERSON_ERASE or WORKSPACE_MANAGE: a firm may want someone who answers subject access
-- requests to export without being able to delete, or to run the workspace's settings. Seeded to ADMIN alone, since
-- the zip holds a person's whole dossier — notes, call transcripts and AI readings included.

INSERT INTO app_lm_action (scope, name, description)
VALUES ('WORKSPACE', 'PERSON_SUBJECT_ACCESS', 'Export everything held about a person, to answer their request for it')
ON CONFLICT (scope, name) DO NOTHING;

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM app_lm_role r
JOIN app_lm_action a ON a.scope = 'WORKSPACE' AND a.name = 'PERSON_SUBJECT_ACCESS'
WHERE r.scope = 'WORKSPACE' AND r.name = 'ADMIN'
ON CONFLICT (role_id, action_id) DO NOTHING;
