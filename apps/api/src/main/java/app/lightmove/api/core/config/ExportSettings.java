package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How much one Companies export may carry — {@code lightmove.export.*}. Past either ceiling it is
 * <b>refused</b>, not truncated, as {@link SpreadsheetImportSettings} is: half a file still looks whole.
 */
public record ExportSettings(
        @DefaultValue("5000") int maxCompanies,
        @DefaultValue("10000") int maxCandidates
) {}
