package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How much one export of a mandate's Companies grid may carry — {@code lightmove.export.*}.
 *
 * <p>Its own block rather than borrowing the talent map's caps: a ceiling named for the globe would
 * be a lie the next reader has to untangle.
 *
 * <p>Past either ceiling the export is <b>refused</b>, not truncated — the rule
 * {@link SpreadsheetImportSettings} already states for the other direction, and it matters more here:
 * a spreadsheet missing its second half still looks like a complete spreadsheet.
 */
public record ExportSettings(
        @DefaultValue("5000") int maxCompanies,
        @DefaultValue("10000") int maxCandidates
) {}
