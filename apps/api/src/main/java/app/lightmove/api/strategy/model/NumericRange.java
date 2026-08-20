package app.lightmove.api.strategy.model;

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
 */
public record NumericRange(Long min, Long max) {

    /** True when neither end is set, in which case the axis is unconstrained. */
    public boolean isEmpty() {
        return min == null && max == null;
    }
}
