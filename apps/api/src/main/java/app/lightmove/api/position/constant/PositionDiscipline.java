package app.lightmove.api.position.constant;

/**
 * The function a role template belongs to — how the template picker groups its options. Coarser than
 * a job title on purpose, and grouping is all it decides; nothing branches on it.
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
