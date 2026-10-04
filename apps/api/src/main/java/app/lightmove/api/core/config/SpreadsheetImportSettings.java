package app.lightmove.api.core.config;

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
