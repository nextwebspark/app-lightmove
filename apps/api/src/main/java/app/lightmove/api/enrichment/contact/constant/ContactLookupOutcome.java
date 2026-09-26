package app.lightmove.api.enrichment.contact.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** What one Find press did. {@link #HELD} and a remembered {@link #NONE} spend nothing. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ContactLookupOutcome {

    FOUND("found"),

    /** Remembered, so the provider is asked once and never again. */
    NONE("none"),

    /** Values were already held; nothing was asked. */
    HELD("held");

    private final String value;
}
