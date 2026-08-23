package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Read and write limits around the company universe ({@code app_lm_apollo_companies}): the pickers'
 * typeahead, the Strategy screen's paged list, how many companies one "Add all to Universe" may take,
 * and how long any of it may be held in memory. Defaults are production-ready; each is overridable per
 * environment under {@code lightmove.company.*} without a recompile.
 */
public record CompanySettings(
        CompanySearchSettings search,
        CompanyListSettings list,

        /** Without {@code @DefaultValue} an absent {@code cache:} block binds to null, not defaults. */
        @DefaultValue CompanyCacheSettings cache
) {}
