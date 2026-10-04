package app.lightmove.api.report.constant;

/**
 * A seatless workspace admin is a {@link #LEAD}, as {@code ProjectAccess} treats them; {@link #FORMER}
 * filed executives but holds no seat any more, and their work stays counted.
 */
public enum ResearcherRole {
    LEAD,
    RESEARCHER,
    FORMER
}
