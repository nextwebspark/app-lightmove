-- A sequence's own sending schedule: the days and hours its emails may go, read in the sender's zone.
-- send_days is an ISO weekday bitmask (Monday bit 0 … Sunday bit 6); every existing sequence keeps the
-- working week every deployment ships with, Monday to Friday, 08:00 to 18:00.
ALTER TABLE app_lm_outreach_sequence
    ADD COLUMN send_days    integer  NOT NULL DEFAULT 31
        CONSTRAINT app_lm_outreach_sequence_send_days_chk CHECK (send_days BETWEEN 1 AND 127),
    ADD COLUMN window_start time     NOT NULL DEFAULT '08:00',
    ADD COLUMN window_end   time     NOT NULL DEFAULT '18:00',
    ADD CONSTRAINT app_lm_outreach_sequence_window_chk CHECK (window_start < window_end);

-- A follow-up's time of day; null sends it at the time of day the step before it went.
ALTER TABLE app_lm_outreach_sequence_step
    ADD COLUMN send_time time;

-- The consultant chose when the first email goes (Now, or a date and time), so the sending window does
-- not move it. The daily cap still does, and every follow-up keeps to the window.
--
-- send_offset_seconds is how far behind the first person of its Start this one was due. A send held for
-- the window or the cap waits that much past the opening, so a batch deferred together stays spaced.
ALTER TABLE app_lm_outreach_enrollment
    ADD COLUMN first_send_pinned   boolean NOT NULL DEFAULT false,
    ADD COLUMN send_offset_seconds integer NOT NULL DEFAULT 0
        CONSTRAINT app_lm_outreach_enrollment_send_offset_chk CHECK (send_offset_seconds >= 0);
