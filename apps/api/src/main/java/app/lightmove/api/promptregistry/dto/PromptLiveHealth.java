package app.lightmove.api.promptregistry.dto;

import app.lightmove.api.promptregistry.constant.PromptHealthWarning;
import java.time.LocalDate;
import java.util.List;

/**
 * The live version over its first {@code days} days live from {@code liveSince} (a UTC day), beside the version it
 * replaced over as many days before. {@code daysLive} is above {@code days} once the comparison stops short of today.
 * A rate is null where nothing was counted. {@code warnings} is empty unless both sides have enough to compare.
 */
public record PromptLiveHealth(
        List<PromptHealthWarning> warnings,
        int comparedWithVersion,
        LocalDate liveSince,
        int days,
        int daysLive,
        long liveCalls,
        Double liveFailureRate,
        long previousCalls,
        Double previousFailureRate,
        long liveDecisions,
        Double liveAcceptRate,
        long previousDecisions,
        Double previousAcceptRate
) {
}
