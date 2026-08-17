package app.lightmove.api.project.dto;

/** How many portal viewers a client has, split by whether their invite is redeemed. */
public record ViewerSummary(long active, long invited) {}
