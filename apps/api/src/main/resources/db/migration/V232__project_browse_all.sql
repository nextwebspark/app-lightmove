-- Positions are seen by the people on them: an admin sees every position of the workspace, anyone else only
-- the positions they hold a seat on. PROJECT_BROWSE stays with ADMIN and MEMBER, because the reference data a member
-- needs to work (currencies, countries, templates, the company search) rides it; seeing every position is its own
-- action, held by ADMIN alone.

INSERT INTO app_lm_action (scope, name, description)
VALUES ('WORKSPACE', 'PROJECT_BROWSE_ALL', 'See every position of the workspace, not only the ones you are on')
ON CONFLICT (scope, name) DO NOTHING;

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM app_lm_role r
JOIN app_lm_action a ON a.scope = 'WORKSPACE' AND a.name = 'PROJECT_BROWSE_ALL'
WHERE r.scope = 'WORKSPACE' AND r.name = 'ADMIN'
ON CONFLICT (role_id, action_id) DO NOTHING;
