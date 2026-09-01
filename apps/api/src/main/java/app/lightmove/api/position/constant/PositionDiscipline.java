package app.lightmove.api.position.constant;

/**
 * The function a role template belongs to — how the template picker groups its options.
 *
 * <p>Coarser than a job title on purpose: a Chief Compliance Officer, a General Counsel and a Head of
 * Internal Audit are three templates and one {@link #GOVERNANCE} group, because a consultant looking
 * for any of them is looking in the same place. Grouping is all this decides; nothing branches on it.
 */
public enum PositionDiscipline {

    /** The chief executive's own seat, and the generic executive brief. */
    EXECUTIVE,

    FINANCE,

    OPERATIONS,

    TECHNOLOGY,

    /** HR, talent and organisation design. */
    PEOPLE,

    /** Revenue-facing: commercial, sales and marketing. */
    COMMERCIAL,

    /** The control functions — compliance, legal, risk and internal audit. */
    GOVERNANCE,

    /** Investment management: allocation, origination and portfolio. */
    INVESTMENT
}
