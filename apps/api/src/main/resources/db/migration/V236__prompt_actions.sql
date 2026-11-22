-- The platform console's Prompts section (#1317). Two actions, so that who may put a version live can later be
-- narrowed without changing what saving one means.

INSERT INTO app_lm_action (scope, name, description)
VALUES ('PLATFORM', 'PROMPT_MANAGE',
        'Read the AI prompts and their versions, save a new version and try it'),
       ('PLATFORM', 'PROMPT_PROMOTE',
        'Put a prompt version live for every workspace, or roll back to an earlier one')
ON CONFLICT (scope, name) DO NOTHING;

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM app_lm_role r
JOIN app_lm_action a ON a.scope = 'PLATFORM' AND a.name IN ('PROMPT_MANAGE', 'PROMPT_PROMOTE')
WHERE r.scope = 'PLATFORM' AND r.name = 'SUPER_ADMIN'
ON CONFLICT (role_id, action_id) DO NOTHING;
