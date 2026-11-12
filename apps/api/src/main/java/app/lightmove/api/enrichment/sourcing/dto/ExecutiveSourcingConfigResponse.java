package app.lightmove.api.enrichment.sourcing.dto;

import java.util.List;

/**
 * Whether Find executives is offered, how many companies one search may take, the live profiles a company may buy
 * as the panel offers them, and how long a run may stay in progress before the screen reads it as lost.
 */
public record ExecutiveSourcingConfigResponse(boolean enabled, int maxCompaniesPerRun, List<Integer> perCompanyChoices,
                                              int defaultPerCompany, long lostAfterSeconds) {}
