package app.lightmove.api.core.audit.constant;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** The rule a {@code PERSON_ERASED} line names in its {@code reason}; an admin's own erasure names none. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PersonErasureReason {

    RETENTION("retention"),
    NEVER_CONTACTED("retention-never-contacted");

    private final String value;

    /** Null for no reason, or one no rule writes. */
    public static PersonErasureReason fromValueOrNull(String value) {
        return Arrays.stream(values()).filter(reason -> reason.value.equals(value)).findFirst().orElse(null);
    }
}
