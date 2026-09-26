package app.lightmove.api.enrichment.contact.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * What one press of a Find button did — and, read with the audit row's {@code billed} detail, what it
 * cost. {@link #HELD} and a remembered {@link #NONE} spend nothing: the answer came off the row.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ContactLookupOutcome {

    /** The provider answered with values. */
    FOUND("found"),

    /** The provider has nothing on this person. Remembered, so it is asked once and never again. */
    NONE("none"),

    /** We already had values. Nothing was asked. */
    HELD("held");

    private final String value;
}
