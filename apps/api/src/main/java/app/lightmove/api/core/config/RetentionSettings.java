package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How long system rows holding personal data are kept — {@code lightmove.retention.*}, one value per row of
 * {@code docs/privacy/retention-schedule.md}. The defaults await counsel's sign-off (#921, ⚖️); every one is an
 * environment override in {@code application.yml}.
 */
public record RetentionSettings(
        /** How often the sweep runs. Spelled out in yml: {@code @Scheduled} reads it. */
        @DefaultValue("PT1H") String sweepInterval,

        /** A refresh-token family, its IP address and browser line, kept this long after its last token expired. */
        @DefaultValue("30d") Duration refreshTokensAfter,

        /** A verification or password-reset link, kept this long after it expired. */
        @DefaultValue("30d") Duration verificationTokensAfter,

        /** A two-factor sign-in challenge, kept this long after it expired. */
        @DefaultValue("1d") Duration mfaChallengesAfter,

        /** An invitation, kept this long after it was accepted, or after it expired unaccepted. */
        @DefaultValue("90d") Duration invitationsAfter,

        /** A bell notification, kept this long after it was read or archived; an unread one is kept. */
        @DefaultValue("90d") Duration notificationsAfter,

        /** The record that a day's digest went, kept this long after it went. */
        @DefaultValue("90d") Duration digestsAfter,

        /** An assistant chat and its turns, kept this long after it was last written to. */
        @DefaultValue("180d") Duration assistantThreadsAfter,

        /** A metered use (search, AI, finds), kept this long: a year of fair-use months to compare, and one more. */
        @DefaultValue("400d") Duration usageEventsAfter
) {}
