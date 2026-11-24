package app.lightmove.api.core.llm.model;

import java.time.LocalDate;

/**
 * A prompt's live version over its first {@code days} days live, beside the version it replaced over as many days
 * before. {@code liveSince} is the UTC day it went live; {@code daysLive} counts from there to today, so it is above
 * {@code days} once the comparison stops short of today. {@code failures} are calls that ended cut off, stopped by
 * the safety filter, or with an answer that would not bind.
 */
public record LiveVersionComparison(
        String promptId,
        int previousVersion,
        LocalDate liveSince,
        int days,
        int daysLive,
        long liveCalls,
        long liveFailures,
        long previousCalls,
        long previousFailures,
        long liveAccepted,
        long liveChanged,
        long previousAccepted,
        long previousChanged
) {
}
