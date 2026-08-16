package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** The sector-scope match count — {@code GET /api/v1/companies/estimate}. */
public record CompanyEstimateSettings(
        /** Most sector-plus-tag labels one estimate may carry before it is rejected. */
        @DefaultValue("100") int maxLabels
) {}
