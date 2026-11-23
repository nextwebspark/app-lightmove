package app.lightmove.api.promptregistry.service;

import app.lightmove.api.core.llm.model.LiveVersionComparison;
import app.lightmove.api.promptregistry.constant.PromptHealthWarning;
import app.lightmove.api.promptregistry.dto.PromptLiveHealth;
import java.util.ArrayList;
import java.util.List;

/**
 * When a live version counts as clearly worse than the one it replaced. Both sides need enough calls or decisions
 * for a rate to mean anything, and the gap has to be wider than day-to-day noise.
 */
final class PromptHealthRules {

    /** The longest span compared on either side of the move. */
    static final int COMPARISON_DAYS = 30;

    static final long MIN_CALLS = 50;
    static final double FAILURE_RATE_MARGIN = 0.05;

    static final long MIN_DECISIONS = 20;
    static final double ACCEPT_RATE_MARGIN = 0.10;

    private PromptHealthRules() {
    }

    static PromptLiveHealth healthOf(LiveVersionComparison compared) {
        long liveDecisions = compared.liveAccepted() + compared.liveChanged();
        long previousDecisions = compared.previousAccepted() + compared.previousChanged();
        Double liveFailureRate = rateOf(compared.liveFailures(), compared.liveCalls());
        Double previousFailureRate = rateOf(compared.previousFailures(), compared.previousCalls());
        Double liveAcceptRate = rateOf(compared.liveAccepted(), liveDecisions);
        Double previousAcceptRate = rateOf(compared.previousAccepted(), previousDecisions);

        List<PromptHealthWarning> warnings = new ArrayList<>(2);
        if (compared.liveCalls() >= MIN_CALLS && compared.previousCalls() >= MIN_CALLS
                && liveFailureRate - previousFailureRate >= FAILURE_RATE_MARGIN) {
            warnings.add(PromptHealthWarning.MORE_FAILURES);
        }
        if (liveDecisions >= MIN_DECISIONS && previousDecisions >= MIN_DECISIONS
                && previousAcceptRate - liveAcceptRate >= ACCEPT_RATE_MARGIN) {
            warnings.add(PromptHealthWarning.FEWER_ACCEPTED);
        }
        return new PromptLiveHealth(List.copyOf(warnings), compared.previousVersion(), compared.days(),
                compared.liveCalls(), liveFailureRate, compared.previousCalls(), previousFailureRate,
                liveDecisions, liveAcceptRate, previousDecisions, previousAcceptRate);
    }

    private static Double rateOf(long part, long whole) {
        return whole == 0 ? null : (double) part / whole;
    }
}
