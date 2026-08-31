package app.lightmove.api.core.config;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The spreadsheet a mandate can import into its Companies grid —
 * {@code lightmove.spreadsheet-import.*}. Not {@code lightmove.import.*}: {@code import} is a Java
 * keyword and so cannot be a record component, and a yml key that does not match its component name
 * is exactly the drift this typed tree exists to prevent.
 *
 * <p>The allowlist is checked <b>server-side</b> for the same reason
 * {@link PositionDocumentSettings}'s is: the content type on a multipart part is a claim made by
 * whatever sent the request, so it decides nothing on its own. Here it also cannot decide much even
 * when honest — browsers disagree about what a {@code .csv} is, and several send
 * {@code application/vnd.ms-excel} for one — so the reader sniffs the bytes as well and this list only
 * keeps obviously wrong uploads out.
 */
public record SpreadsheetImportSettings(
        /**
         * Ceiling on one upload, in bytes. The same modest figure as a position description, and for
         * the same reason: the bytes travel through the request thread and are held whole in memory
         * while the sheet is read.
         */
        @DefaultValue("10485760") long maxFileSizeBytes,

        /**
         * Ceiling on data rows in one import. Refused whole rather than truncated — taking the first
         * N would silently decide which half of a consultant's list got imported.
         */
        @DefaultValue("5000") int maxRows,

        /**
         * Whether a few sample cell values may be sent to the model alongside the headers when it
         * proposes a column mapping.
         *
         * <p><b>Off.</b> A spreadsheet of executives is exactly the candidate and client PII that is
         * deliberately kept out of the application log, and a header plus a locally-computed type hint
         * carries nearly all the signal a mapping needs. The switch exists so an operator can make
         * that trade knowingly rather than by editing code.
         */
        @DefaultValue("false") boolean sendSampleValues,

        /** The document types an import may arrive as. */
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
        // @DefaultValue on a List binds an operator's empty override to [""], not to [] — the trap that
        // once emptied the consumer-domain blocklist. An allowlist of one blank string accepts nothing,
        // so it is refused loudly here rather than silently rejecting every upload.
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
