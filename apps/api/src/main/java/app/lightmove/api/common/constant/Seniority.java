package app.lightmove.api.common.constant;

/**
 * How far a seat sits from the chief executive — the axis executive search is written in, because the
 * same job title means different things in a family holding and in a listed multinational.
 *
 * <p>The two tiers above the executive line are named rather than numbered: a board seat and an
 * executive-committee seat are different kinds of role, not two distances from the same point.
 *
 * <p><b>One ladder, read from both ends.</b> A position brief states the seniority of the seat it is
 * searching for; a candidate row states the seniority of the person who might fill it. They are
 * answering the same question, so they share this enum — two copies would let a tier be added to one
 * and not the other, and the two would quietly stop matching.
 *
 * <p><b>Two wire formats, on purpose.</b> The candidate API speaks {@link #value()} — "N-1", which is
 * what a consultant writes — because that contract shipped that way. The position API speaks the enum
 * name, because that is what its screen was built against. Both are stored as the name, so the
 * database has one spelling regardless.
 */
public enum Seniority {

    /** A non-executive seat: chair, board member, advisor to the board. */
    BOARD("Board"),

    /** The executive committee — the chief executive of the business or the region, and their peers. */
    C_SUITE("C-Suite"),

    /** Reports to the C-suite — the functional heads a mandate most often targets. */
    N_MINUS_1("N-1"),

    N_MINUS_2("N-2"),

    N_MINUS_3("N-3");

    private final String wireToken;

    Seniority(String wireToken) {
        this.wireToken = wireToken;
    }

    /** The token the candidate contract speaks, and what a consultant would write by hand. */
    public String value() {
        return wireToken;
    }

    /** Resolve a wire token to its tier, or {@code null} if unknown. */
    public static Seniority fromValue(String value) {
        for (Seniority tier : values()) {
            if (tier.wireToken.equals(value)) {
                return tier;
            }
        }
        return null;
    }
}
