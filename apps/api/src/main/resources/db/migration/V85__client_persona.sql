-- An agency client's persona — main business, sectors, competitors, geographies, notes — for the
-- assistant to tailor research to, as V69 gives the firm its own. One jsonb document for V30's reason:
-- it is read and written whole, and never queried by field. In-house business units carry it unused.
ALTER TABLE app_lm_client
    ADD COLUMN persona jsonb NOT NULL DEFAULT '{}'::jsonb;
