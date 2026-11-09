package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How long system rows holding personal data are kept, one value per row of {@code docs/privacy/retention-schedule.md}.
 * The defaults await counsel's sign-off (#921, ⚖️).
 */
public record RetentionSettings(
        /** Spelled out in yml: {@code @Scheduled} reads it. */
        @DefaultValue("PT1H") String sweepInterval,
        @DefaultValue("30d") Duration refreshTokensAfter,
        @DefaultValue("30d") Duration verificationTokensAfter,
        @DefaultValue("1d") Duration mfaChallengesAfter,
        @DefaultValue("90d") Duration invitationsAfter,
        @DefaultValue("90d") Duration notificationsAfter,
        @DefaultValue("90d") Duration digestsAfter,
        @DefaultValue("180d") Duration assistantThreadsAfter,
        @DefaultValue("400d") Duration usageEventsAfter
) {}
