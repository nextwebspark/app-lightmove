package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Reading an attached position description into proposals for steps one, two, three and five — {@code
 * lightmove.position.extraction.*}.
 *
 * <p>Caps here bound both a decompression bomb and the model's own per-call cost — {@code maxPages}
 * against the PDF itself, {@code maxCharacters} against the text every format produces, since the
 * 10 MB upload ceiling does not bound how much text a well-compressed file decompresses into. Both
 * are refused whole rather than read in part: a truncated read is a silent guess at which half of
 * the document mattered.
 */
public record PositionExtractionSettings(
        /** Off leaves the upload/download path untouched and turns "Read from document" off alone. */
        @DefaultValue("true") boolean enabled,

        /** A position description is a handful of pages; this is generous for one and stingy for a library. */
        @DefaultValue("40000") int maxCharacters,

        /** Generous for a mandate-length brief, stingy for a library someone attached by mistake. */
        @DefaultValue("60") int maxPages,

        /**
         * Whether the client's registered name, its common-suffix variants and its domain are
         * replaced with placeholders before the redacted text reaches the model. See {@code
         * PositionDocumentRedactor}.
         */
        @DefaultValue("true") boolean redactKnownCompanyNames,

        /**
         * Whether a document's contact details are kept from the model: both the block sweep (an
         * email or phone number, plus the lines around it) and the email/URL/phone pseudonymisation
         * applied to whatever text survives that sweep. One flag for both — see {@code
         * PositionDocumentRedactor}.
         */
        @DefaultValue("true") boolean redactContactDetails
) {

    public PositionExtractionSettings {
        if (maxCharacters < 1) {
            throw new IllegalArgumentException(
                    "lightmove.position.extraction.max-characters must be positive, but was "
                            + maxCharacters);
        }
        if (maxPages < 1) {
            throw new IllegalArgumentException(
                    "lightmove.position.extraction.max-pages must be positive, but was " + maxPages);
        }
    }
}
