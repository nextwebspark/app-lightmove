package app.lightmove.api.report.constant;

/**
 * The seat a researcher holds on the mandate, as the performance table labels them. {@link #FORMER}
 * is someone who filed executives and no longer holds a staff seat — their work stays counted.
 */
public enum ResearcherRole {
    LEAD,
    RESEARCHER,
    FORMER
}
