package app.lightmove.api.candidate.model;

/** Whether a vendor holds each kind of contact — flags only; revealing one is a separate, paid call. */
public record ResearchedContactAvailability(boolean personalEmail, boolean workEmail, boolean phone) {}
