package app.lightmove.api.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** The employment arrangement of a mandate — a closed set, mirrored by the Position screen's chips. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum EmploymentType {
    FULL_TIME_PERMANENT("full-time permanent"),
    FIXED_TERM_CONTRACT("fixed-term contract"),
    PART_TIME("part-time"),
    TEMPORARY("temporary"),
    INTERIM("interim"),
    RETAINED_ADVISORY("retained advisory");

    /** How the arrangement is named to the model. */
    private final String promptLabel;
}
