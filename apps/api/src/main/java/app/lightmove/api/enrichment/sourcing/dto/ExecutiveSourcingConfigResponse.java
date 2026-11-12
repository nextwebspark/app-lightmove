package app.lightmove.api.enrichment.sourcing.dto;

import java.util.List;

/**
 * Whether Find executives is offered, how many companies one search may take, the live profiles a company may buy
 * as the panel offers them, how long a run may stay in progress before the screen reads it as lost, and how many
 * companies a run searches at once.
 */
public record ExecutiveSourcingConfigResponse(boolean enabled, int maxCompaniesPerRun, List<Integer> perCompanyChoices,
                                              int defaultPerCompany, long lostAfterSeconds, int parallelism) {}
