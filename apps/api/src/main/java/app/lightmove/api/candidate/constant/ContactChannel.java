package app.lightmove.api.candidate.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Which contact a value is — and which a lookup is after, since the two bill from separate pools and
 * are asked for separately. Stored by name, matching V54's CHECK.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ContactChannel {

    EMAIL("email"),
    PHONE("phone");

    private final String value;
}
