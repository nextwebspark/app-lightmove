package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Reading an attached position description into step-one proposals — {@code
 * lightmove.position.extraction.*}.
 *
 * <p>Caps here bound both a decompression bomb and the model's own per-call cost: {@code maxPages} is
 * checked against the PDF itself, {@code maxCharacters} against the text every format produces —
 * the 10 MB upload ceiling does not bound how much text a well-compressed file decompresses into.
 */
public record PositionExtractionSettings(
        /** Off leaves the upload/download path untouched and turns "Read from document" off alone. */
        @DefaultValue("true") boolean enabled,

        /** A position description is a handful of pages; this is generous for one and stingy for a library. */
        @DefaultValue("40000") int maxCharacters,

        /** Refused whole rather than read in part — a truncated read is a silent guess at which half mattered. */
        @DefaultValue("60") int maxPages,

        /**
         * Whether the client's registered name, its common-suffix variants and its domain are
         * replaced with placeholders before the redacted text reaches the model. See {@code
         * PositionDocumentRedactor}.
         */
        @DefaultValue("true") boolean redactKnownCompanyNames,

        /**
         * Whether a contact-details block (an email or phone number, plus the lines around it) is
         * stripped before the text reaches the model. See {@code PositionDocumentRedactor}.
         */
        @DefaultValue("true") boolean stripContactBlocks
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
