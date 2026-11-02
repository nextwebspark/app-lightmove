package app.lightmove.api.core.config;

import java.time.Duration;
import java.util.List;
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
        @DefaultValue("400d") Duration usageEventsAfter,

        /**
         * How long a candidate nobody is working with is kept before they wait for an admin's review, in months, where
         * a workspace has not chosen; null is never. One of {@link #CANDIDATE_MONTHS}.
         */
        @DefaultValue("24") Integer candidateMonths,

        /**
         * A deleted workspace's grace period: members lose access at once, and everything is purged this long after.
         * Uncava can restore it until then.
         */
        @DefaultValue("30d") Duration deletedWorkspacesAfter,

        /** How often due workspaces are purged. Spelled out in yml: {@code @Scheduled} reads it. */
        @DefaultValue("PT15M") String workspacePurgeInterval,

        /** A whole-workspace export's zip, downloadable this long after it was built. */
        @DefaultValue("7d") Duration workspaceExportsAfter,

        /** An export still building this long after it started is failed: its worker died with its instance. */
        @DefaultValue("2h") Duration workspaceExportDeadline
) {

    public RetentionSettings {
        if (candidateMonths != null && !CANDIDATE_MONTHS.contains(candidateMonths)) {
            throw new IllegalArgumentException("lightmove.retention.candidate-months must be one of "
                    + CANDIDATE_MONTHS + ", or blank for never");
        }
    }

    /** The periods a workspace may choose, and so the only ones a deployment may default to. */
    public static final List<Integer> CANDIDATE_MONTHS = List.of(12, 24, 36, 60);
}
