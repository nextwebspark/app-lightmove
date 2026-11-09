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
        @DefaultValue("90d") Duration invitationsAfter,
        @DefaultValue("90d") Duration notificationsAfter,
        @DefaultValue("90d") Duration digestsAfter,
        @DefaultValue("180d") Duration assistantThreadsAfter,
        @DefaultValue("400d") Duration usageEventsAfter,

        /** Where a workspace has not chosen; null is never. One of {@link #CANDIDATE_MONTHS}. */
        @DefaultValue("24") Integer candidateMonths
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
