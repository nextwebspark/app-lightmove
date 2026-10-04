package app.lightmove.api.enrichment.sourcing.dto;

/**
 * Whether the screen offers Find executives, the numbers its confirm dialog states, and how long a run
 * may stay in progress before the screen reads it as lost.
 */
public record ExecutiveSourcingConfigResponse(boolean enabled, int maxCompaniesPerRun, int hitsPerCompany,
                                              int picksPerCompany, long lostAfterSeconds) {}
