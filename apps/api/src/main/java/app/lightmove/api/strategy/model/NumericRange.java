package app.lightmove.api.strategy.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A custom, caller-authored range on one numeric axis — the wireframe's "Custom Range" mode, where a
 * consultant types a headcount or a revenue figure instead of picking a predefined band.
 *
 * <p>Both ends are optional and both are inclusive. {@code min} alone reads "at least", {@code max}
 * alone "at most", and a range with neither end set is no constraint at all rather than an error: a
 * half-typed pair must narrow the search progressively, not reject it.
 *
 * <p>A range and its axis's band list are <b>mutually exclusive by construction</b>. There is no mode
 * flag anywhere in the filter — a non-null range <i>is</i> the custom mode, and switching back to
 * predefined clears it. One representation, so the two can never disagree about which is in force.
 *
 * <p><b>Both Jackson annotations are load-bearing.</b> This record is nested inside the {@code filter}
 * jsonb column, and Jackson reads {@code isEmpty()} as a bean property: it wrote {@code "empty"} into
 * every stored document and then refused to read one back, so saving a Custom Range on either axis
 * left the mandate unreadable — the Strategy screen, its results, the report and bulk add all 500ing
 * on the next request. {@code @JsonIgnore} stops it being written; {@code ignoreUnknown} keeps the
 * documents already carrying it readable. Any derived accessor added here needs the same treatment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NumericRange(Long min, Long max) {

    /** True when neither end is set, in which case the axis is unconstrained. */
    @JsonIgnore
    public boolean isEmpty() {
        return min == null && max == null;
    }
}
