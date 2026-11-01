-- The OAuth client a direct mailbox or a Zoom account was connected through. A refresh token belongs to the client it
-- was issued to, so when a workspace changes its app at a provider every connection made through the old one must
-- connect again, and nothing recorded which those were. Null where unknown: a Nylas mailbox (Nylas's own app), or a
-- connection made before this column through Uncava's shared app, whose client id lives in configuration and not
-- here. A null is read as "through the app the workspace used until now".

ALTER TABLE app_lm_mailbox_connection
    ADD COLUMN app_client_id varchar(255);

ALTER TABLE app_lm_zoom_connection
    ADD COLUMN app_client_id varchar(255);

UPDATE app_lm_mailbox_connection m
SET app_client_id = i.client_id
FROM app_lm_workspace_mail_integration i
WHERE m.gateway = 'DIRECT'
  AND i.workspace_id = m.workspace_id
  AND i.provider = upper(m.provider)
  AND i.mode = 'OWN';

UPDATE app_lm_zoom_connection z
SET app_client_id = i.client_id
FROM app_lm_workspace_mail_integration i
WHERE i.workspace_id = z.workspace_id
  AND i.provider = 'ZOOM'
  AND i.mode = 'OWN';
