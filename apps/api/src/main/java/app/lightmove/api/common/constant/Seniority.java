package app.lightmove.api.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * How far a seat sits from the chief executive — the axis executive search is written in, because the
 * same job title means different things in a family holding and in a listed multinational. The two
 * tiers above the executive line are named rather than numbered.
 *
 * <p><b>One ladder, read from both ends.</b> A brief states the seniority of the seat and a candidate
 * row the seniority of the person, so they share this enum rather than letting a tier be added to one
 * and not the other.
 *
 * <p><b>Two wire formats.</b> The candidate API speaks {@link #value()} ("N-1"), the position API the
 * enum name. Both are stored as the name, so the database has one spelling.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum Seniority implements ApiValueEnum {

    /** A non-executive seat: chair, board member, advisor to the board. */
    BOARD("Board"),

    /** The executive committee — the chief executive of the business or the region, and their peers. */
    C_SUITE("C-Suite"),

    /** Reports to the C-suite — the functional heads a mandate most often targets. */
    N_MINUS_1("N-1"),

    N_MINUS_2("N-2"),

    N_MINUS_3("N-3");

    /** The token the candidate contract speaks, and what a consultant would write by hand. */
    private final String value;

    /** Resolve a wire token to its tier, or {@code null} if unknown. */
    public static Seniority fromValue(String value) {
        return ApiValueEnum.fromValue(Seniority.class, value);
    }
}
