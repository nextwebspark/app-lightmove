package app.lightmove.api.core.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How long system rows holding personal data are kept, one value per row of {@code docs/gdpr/retention-schedule.md}.
 * The defaults await counsel's sign-off (#921, ⚖️).
 */
public record RetentionSettings(
        /** Spelled out in yml: {@code @Scheduled} reads it. */
        @DefaultValue("PT1H") String sweepInterval,
        @DefaultValue("30d") Duration refreshTokensAfter,
        @DefaultValue("30d") Duration verificationTokensAfter,
        @DefaultValue("1d") Duration mfaChallengesAfter,
        @DefaultValue("1d") Duration pendingRegistrationsAfter,
        @DefaultValue("90d") Duration invitationsAfter,
        @DefaultValue("90d") Duration notificationsAfter,
        @DefaultValue("90d") Duration digestsAfter,
        @DefaultValue("180d") Duration assistantThreadsAfter,
        @DefaultValue("400d") Duration usageEventsAfter,

        /** Where a workspace has not chosen; null is never. One of {@link #CANDIDATE_MONTHS}. */
        @DefaultValue("24") Integer candidateMonths,

        /** A deleted workspace's grace period: members lose access at once, and everything is purged this long after. */
        @DefaultValue("30d") Duration deletedWorkspacesAfter,

        /** Spelled out in yml: {@code @Scheduled} reads it. */
        @DefaultValue("PT15M") String workspacePurgeInterval,

        /** A whole-workspace export's zip, downloadable this long after it was built. */
        @DefaultValue("7d") Duration workspaceExportsAfter,

        /** An export still building this long after it started is failed: its worker died with its instance. */
        @DefaultValue("2h") Duration workspaceExportDeadline,

        /**
         * Grey and never contacted (#1490, B2/E1): erased this many months after they were added, or after the last
         * position they were on was archived. Null turns the pass off.
         */
        @DefaultValue("6") Integer neverContactedMonths,

        /** At most this many erased per pass, so the first run after switching retention on stays small. */
        @DefaultValue("500") int neverContactedPerRun,

        /** Spelled out in yml: {@code @Scheduled} reads it. */
        @DefaultValue("0 40 2 * * *") String neverContactedScan
) {

    public RetentionSettings {
        if (candidateMonths != null && !CANDIDATE_MONTHS.contains(candidateMonths)) {
            throw new IllegalArgumentException("lightmove.retention.candidate-months must be one of "
                    + CANDIDATE_MONTHS + ", or blank for never");
        }
        if (neverContactedMonths != null && neverContactedMonths < 1) {
            throw new IllegalArgumentException("lightmove.retention.never-contacted-months must be at least 1, "
                    + "or blank for never");
        }
    }

    /** The periods a workspace may choose, and so the only ones a deployment may default to. */
    public static final List<Integer> CANDIDATE_MONTHS = List.of(12, 24, 36, 60);
}
