-- Client access on a mandate becomes its own permission, held by the project LEAD alone.
--
-- Admitting an outside contact to a search and changing the search's target date were one action,
-- PROJECT_EDIT — "change the mandate: target date, stage transitions". The rule we want is already
-- true, but only by arithmetic: LEAD holds PROJECT_EDIT and RESEARCHER does not. Widen PROJECT_EDIT
-- to RESEARCHER one day and client access goes with it, silently, with nothing to fail.
--
-- The two tiers of client work stay deliberately apart. The registry — client records and their
-- representatives — is workspace CLIENT_RECORD_MANAGE, held by ADMIN and MEMBER, and creating a
-- representative there shows them nothing. Seeing a mandate is this action, and it is the lead's.
--
-- The workspace-admin bypass is untouched: an admin is implicitly a lead on every project in their own
-- workspace, so a departed mandate owner can never strand a search.

INSERT INTO app_lm_action (scope, name, description)
VALUES ('PROJECT', 'CLIENT_ACCESS_MANAGE',
        'Give or withdraw a client representative''s read-only view of this mandate');

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM app_lm_role r
JOIN app_lm_action a ON a.scope = 'PROJECT' AND a.name = 'CLIENT_ACCESS_MANAGE'
WHERE r.scope = 'PROJECT' AND r.name = 'LEAD';
