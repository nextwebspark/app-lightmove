package app.lightmove.api.enrichment.sourcing.dto;

/** Whether the screen offers Find executives, and the numbers its confirm dialog states. */
public record ExecutiveSourcingConfigResponse(boolean enabled, int maxCompaniesPerRun, int hitsPerCompany,
                                              int picksPerCompany) {}
