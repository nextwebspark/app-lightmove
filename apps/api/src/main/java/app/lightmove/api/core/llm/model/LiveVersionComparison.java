package app.lightmove.api.core.llm.model;

/**
 * A prompt's live version since it went live, beside the version it replaced over the same number of days before.
 * {@code failures} are calls that ended cut off, stopped by the safety filter, or with an answer that would not bind.
 */
public record LiveVersionComparison(
        String promptId,
        int previousVersion,
        int days,
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
