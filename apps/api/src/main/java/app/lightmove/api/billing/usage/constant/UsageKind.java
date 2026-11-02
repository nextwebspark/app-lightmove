package app.lightmove.api.billing.usage.constant;

import app.lightmove.api.core.config.FairUseSettings;
import app.lightmove.api.core.platformconfig.constant.PlatformSetting;

/**
 * An unpriced action metered for fair use: its per-seat ceiling an allowlisted value a super admin may change, its
 * estimated cost read from {@code fair-use.*}.
 */
public enum UsageKind {
    PEOPLE_SEARCH_PAGE,
    SOURCING_RUN,
    AI_ENRICH,
    OUTREACH_OPENER,
    ASSISTANT_ASK;

    public PlatformSetting perSeatCeiling() {
        return switch (this) {
            case PEOPLE_SEARCH_PAGE -> PlatformSetting.FAIR_USE_PEOPLE_SEARCH_PAGE_PER_SEAT;
            case SOURCING_RUN -> PlatformSetting.FAIR_USE_SOURCING_RUN_PER_SEAT;
            case AI_ENRICH -> PlatformSetting.FAIR_USE_AI_ENRICH_PER_SEAT;
            case OUTREACH_OPENER -> PlatformSetting.FAIR_USE_OUTREACH_OPENER_PER_SEAT;
            case ASSISTANT_ASK -> PlatformSetting.FAIR_USE_ASSISTANT_ASK_PER_SEAT;
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
