package app.lightmove.api.report.constant;

/**
 * The seat a researcher holds on the mandate, as the performance table labels them. A workspace admin
 * with no seat is a {@link #LEAD}, as {@code ProjectAccess} treats them; {@link #FORMER} is someone who
 * filed executives and holds neither any more — their work stays counted.
 */
public enum ResearcherRole {
    LEAD,
    RESEARCHER,
    FORMER
}
