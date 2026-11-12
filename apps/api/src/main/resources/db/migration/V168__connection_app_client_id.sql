-- The OAuth client a direct mailbox or a Zoom account was connected through. A refresh token belongs to the client it
-- was issued to, so when a workspace changes its app at a provider every connection made through the old one must
-- connect again, and nothing recorded which those were. Null where unknown — a Nylas mailbox (Nylas's own app), or
-- any connection made before this column: no backfill can tell, since a workspace may have changed app since. The
-- first refresh an app answers records it (MailboxTokens, ZoomTokens); until then a null is read as "the app the
-- workspace used until now".

ALTER TABLE app_lm_mailbox_connection
    ADD COLUMN app_client_id varchar(255);

ALTER TABLE app_lm_zoom_connection
    ADD COLUMN app_client_id varchar(255);

-- Only an Entra secret expires. A date left on a Google or Zoom row would keep emailing its admins until the next save.
UPDATE app_lm_workspace_mail_integration
SET secret_expires_on = NULL,
    secret_expiry_warned_days = NULL
WHERE provider <> 'MICROSOFT'
  AND secret_expires_on IS NOT NULL;
