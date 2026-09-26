package app.lightmove.api.dataimport.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** What worked out a sheet's column mapping; the mapping step says which, as trust differs. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum MappingSource {

    /** Every header was known; no model call was made. */
    EXACT_HEADERS("exactHeaders"),

    MODEL("model"),

    /** The model could not be reached (the ordinary case without ADC); confident about far less. */
    HEADER_MATCHER("headerMatcher");

    private final String value;
}
