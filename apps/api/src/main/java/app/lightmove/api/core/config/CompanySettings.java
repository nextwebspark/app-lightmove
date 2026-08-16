package app.lightmove.api.core.config;

/**
 * Read limits over the shared company universe (app_lm_companies): the picker search/browse, the
 * sector-scope estimate, and the sector suggestions. Defaults are production-ready; each is
 * overridable per environment under {@code lightmove.company.*} without a recompile.
 */
public record CompanySettings(
        CompanySearchSettings search,
        CompanyEstimateSettings estimate,
        CompanySuggestionSettings suggestions
) {}
