package app.lightmove.api.billing.usage.constant;

import app.lightmove.api.core.config.FairUseSettings;

/** An unpriced action metered for fair use, with its ceiling and estimated cost read from {@code fair-use.*}. */
public enum UsageKind {
    PEOPLE_SEARCH_PAGE,
    SOURCING_RUN,
    AI_ENRICH,
    OUTREACH_OPENER,
    ASSISTANT_ASK;

    public long perSeatIn(FairUseSettings settings) {
        return switch (this) {
            case PEOPLE_SEARCH_PAGE -> settings.peopleSearchPagePerSeat();
            case SOURCING_RUN -> settings.sourcingRunPerSeat();
            case AI_ENRICH -> settings.aiEnrichPerSeat();
            case OUTREACH_OPENER -> settings.outreachOpenerPerSeat();
            case ASSISTANT_ASK -> settings.assistantAskPerSeat();
        };
    }

    public long costFilsIn(FairUseSettings settings) {
        return switch (this) {
            case PEOPLE_SEARCH_PAGE -> settings.peopleSearchPageCostFils();
            case SOURCING_RUN -> settings.sourcingRunCostFils();
            case AI_ENRICH -> settings.aiEnrichCostFils();
            case OUTREACH_OPENER -> settings.outreachOpenerCostFils();
            case ASSISTANT_ASK -> settings.assistantAskCostFils();
        };
    }
}
