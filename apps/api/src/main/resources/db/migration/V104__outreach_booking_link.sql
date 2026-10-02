-- The booking link (epic #620, story #628): a page at <web.base-url>/book/<slug> where an executive picks
-- a time on a consultant's calendar, through a Nylas Scheduler configuration on that consultant's grant.
--
-- The slug is the link's whole identity and outlives a reconnect, so links already sent keep working; the
-- configuration belongs to a grant, so a reconnect clears it and the page makes a new one.
ALTER TABLE app_lm_mailbox_connection
    ADD COLUMN booking_slug              varchar(64),
    ADD COLUMN booking_configuration_id  varchar(128);

CREATE UNIQUE INDEX app_lm_mailbox_connection_booking_slug_uk ON app_lm_mailbox_connection (booking_slug)
    WHERE booking_slug IS NOT NULL;

-- A booking webhook names the configuration and nothing else.
CREATE INDEX app_lm_mailbox_connection_booking_configuration_idx
    ON app_lm_mailbox_connection (booking_configuration_id) WHERE booking_configuration_id IS NOT NULL;

-- A run ended by the executive booking through the link, rather than by a consultant booking for them.
ALTER TABLE app_lm_outreach_enrollment
    ADD COLUMN booked_via_link boolean NOT NULL DEFAULT false;
