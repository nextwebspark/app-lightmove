-- Notetaker 7 (#956): what the daily canary last found on each platform. A canary bot joins a standing test meeting on
-- Meet and on Teams through our own accounts, records for a minute and reports; a platform whose latest canary failed
-- is unhealthy, and while it is, its calls go to the backup recorder where a workspace has turned it on. Platform
-- health is about our bot and the vendors' pages, not any tenant, so this table carries no workspace.
CREATE TABLE app_lm_notetaker_platform_health (
    platform       varchar(20)  PRIMARY KEY
        CONSTRAINT app_lm_notetaker_platform_health_platform_chk
            CHECK (platform IN ('GOOGLE_MEET', 'MICROSOFT_TEAMS', 'ZOOM')),
    healthy        boolean      NOT NULL,
    reason         varchar(64),
    checked_at     timestamptz  NOT NULL,
    -- When the run of failures this one belongs to began; null while healthy.
    failing_since  timestamptz,
    CONSTRAINT app_lm_notetaker_platform_health_reason_chk CHECK (healthy = (reason IS NULL)),
    CONSTRAINT app_lm_notetaker_platform_health_failing_chk CHECK (healthy = (failing_since IS NULL))
);

COMMENT ON TABLE app_lm_notetaker_platform_health IS
    'The Notetaker canary''s latest finding per platform. Not tenant data.';

-- The day's sweep of captured pieces past their age (media-max-age), whatever their run.
CREATE INDEX app_lm_meeting_recording_media_received_idx
    ON app_lm_meeting_recording_media (received_at);
