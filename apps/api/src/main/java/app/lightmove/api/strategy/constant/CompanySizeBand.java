package app.lightmove.api.strategy.constant;

import app.lightmove.api.common.constant.ApiValueEnum;

/** A size band the Strategy filter selects from: a wire token, a label and closed numeric bounds. */
public interface CompanySizeBand extends ApiValueEnum {

    String label();

    /** Smallest value in the band, inclusive, or {@code null} when {@link #isUnknown()}. */
    Long lowerBound();

    /** Largest value in the band, inclusive; {@code null} for an open-ended top band and for Unknown. */
    Long upperBound();

    /** The band that stands for "no figure published" rather than a range. */
    default boolean isUnknown() {
        return false;
    }
}
