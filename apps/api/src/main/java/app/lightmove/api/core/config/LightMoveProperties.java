package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The root of every tunable, as immutable records: a mistyped key fails at startup, not as a
 * {@code null} at 3am. Each branch is its own {@code *Settings} record in this package.
 */
@ConfigurationProperties(prefix = "lightmove")
public record LightMoveProperties(
        AuthSettings auth,
        EmailSettings email,
        WebSettings web,
        CompanySettings company,
        PositionSettings position,
        LlmSettings llm,
        EnrichmentSettings enrichment,
        ResilienceSettings resilience,
        CustomColumnSettings customColumn,
        SpreadsheetImportSettings spreadsheetImport,
        MapboxSettings mapbox,
        TalentMapSettings talentMap,
        ExportSettings export,
        ReportSettings report,
        AssistantSettings assistant
) {}
