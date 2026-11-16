package app.lightmove.api.enrichment.candidate.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** What the Add form's profile lookup answered. Only {@link #FOUND} spent a vendor read. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ProfileLookupOutcome {

    FOUND("found"),

    /** Research holds no such profile: private, removed, or not yet in the dataset. */
    NOT_FOUND("notFound"),

    /** This position already maps that profile; nothing was asked. */
    HELD("held"),

    /** No research provider is configured; nothing was asked. */
    UNAVAILABLE("unavailable");

    private final String value;
}
