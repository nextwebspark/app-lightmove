-- The workspace's people, read and annotated outside any one mandate (docs/candidate-crm.md, decision D2):
-- every staff member, seated on a mandate or not, and never a client representative.

INSERT INTO app_lm_action (scope, name, description)
VALUES ('WORKSPACE', 'CANDIDATE_POOL_MANAGE', 'Candidates: read the workspace''s people, their notes and timeline, and write notes')
ON CONFLICT (scope, name) DO NOTHING;

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM (VALUES ('WORKSPACE', 'ADMIN',  'CANDIDATE_POOL_MANAGE'),
             ('WORKSPACE', 'MEMBER', 'CANDIDATE_POOL_MANAGE')
     ) AS grant_map(scope, role_name, action_name)
JOIN app_lm_role   r ON r.scope = grant_map.scope AND r.name = grant_map.role_name
JOIN app_lm_action a ON a.scope = grant_map.scope AND a.name = grant_map.action_name
ON CONFLICT (role_id, action_id) DO NOTHING;
