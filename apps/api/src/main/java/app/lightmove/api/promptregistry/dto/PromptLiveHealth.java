package app.lightmove.api.promptregistry.dto;

import app.lightmove.api.promptregistry.constant.PromptHealthWarning;
import java.time.LocalDate;
import java.util.List;

/**
 * The live version since it went live ({@code liveSince}, a UTC day) beside the version it replaced, over the same number of days before. A rate is
 * null where nothing was counted. {@code warnings} is empty unless both sides have enough to compare.
 */
public record PromptLiveHealth(
        List<PromptHealthWarning> warnings,
        int comparedWithVersion,
        LocalDate liveSince,
        int days,
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
