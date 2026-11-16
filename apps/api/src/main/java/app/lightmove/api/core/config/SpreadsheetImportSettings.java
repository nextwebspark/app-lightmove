package app.lightmove.api.core.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The spreadsheet import — {@code lightmove.spreadsheet-import.*} ({@code import} is a Java keyword).
 * The content-type allowlist only keeps obviously wrong uploads out; the reader sniffs the bytes too.
 */
public record SpreadsheetImportSettings(
        /** Held whole in memory while the sheet is read. */
        @DefaultValue("10485760") long maxFileSizeBytes,

        /** Refused whole rather than truncated. */
        @DefaultValue("5000") int maxRows,

        /** Off: sample cells are candidate PII, and headers plus a local type hint carry nearly all the signal. */
        @DefaultValue("false") boolean sendSampleValues,

        /**
         * The most distinct LinkedIn profile and company links a file may carry and still be enriched on import: each
         * is a paid vendor read, and a bigger list is enriched person by person rather than all at once.
         */
        @DefaultValue("250") int maxAutoEnrich,

        /** How often the enrichment worker looks for queued rows; spelled out in application.yml for @Scheduled. */
        @DefaultValue("5s") Duration enrichmentTick,

        /** The rows one workspace's pass claims, and how many of them are researched side by side. */
        @DefaultValue("8") int enrichmentBatch,
        @DefaultValue("4") int enrichmentParallelism,

        /** A claimed row its instance never finished is taken again after this. */
        @DefaultValue("10m") Duration enrichmentStaleAfter,

        /** A finished row — a slug and an outcome — is deleted after this. */
        @DefaultValue("30d") Duration enrichmentKeepFor,

        @DefaultValue({
                "text/csv",
                "text/plain",
                "application/csv",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/octet-stream"
        }) List<String> allowedContentTypes
) {

    public SpreadsheetImportSettings {
        if (maxFileSizeBytes < 1) {
            throw new IllegalArgumentException(
                    "lightmove.spreadsheet-import.max-file-size-bytes must be positive, but was " + maxFileSizeBytes);
        }
        if (maxRows < 1) {
            throw new IllegalArgumentException(
                    "lightmove.spreadsheet-import.max-rows must be positive, but was " + maxRows);
        }
        if (maxAutoEnrich < 0 || enrichmentBatch < 1 || enrichmentParallelism < 1) {
            throw new IllegalArgumentException("lightmove.spreadsheet-import.max-auto-enrich must not be negative, "
                    + "and enrichment-batch and enrichment-parallelism must be positive");
        }
        // @DefaultValue("") on a List binds to [""], not [] — see PublicEmailDomains.
        if (allowedContentTypes.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(
                    "lightmove.spreadsheet-import.allowed-content-types must not contain a blank entry");
        }
        allowedContentTypes = List.copyOf(allowedContentTypes);
    }

    public boolean allows(String contentType) {
        return contentType != null && Set.copyOf(allowedContentTypes).contains(contentType);
    }
}
