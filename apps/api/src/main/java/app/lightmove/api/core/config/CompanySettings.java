package app.lightmove.api.core.config;

/**
 * Read and write limits around the company universe ({@code app_lm_apollo_companies}): the pickers'
 * typeahead, the Strategy screen's paged list, and how many companies one "Add all to Universe" may
 * take. Defaults are production-ready; each is overridable per environment under
 * {@code lightmove.company.*} without a recompile.
 */
public record CompanySettings(
        CompanySearchSettings search,
        CompanyListSettings list
) {}
