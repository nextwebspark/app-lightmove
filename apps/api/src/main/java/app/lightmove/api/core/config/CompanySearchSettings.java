package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** The company picker typeahead — {@code GET /api/v1/companies/search}. */
public record CompanySearchSettings(
        /** Rows returned when the request names no explicit {@code limit}. */
        @DefaultValue("10") int defaultResultLimit,

        /** Hard ceiling on rows one search returns; a larger requested {@code limit} clamps to this. */
        @DefaultValue("25") int maxResultLimit,

        /** Longest accepted query text; beyond it the request is rejected — a scope, not an attack. */
        @DefaultValue("100") int maxQueryLength
) {}
