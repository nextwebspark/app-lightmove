package app.lightmove.api.billing.usage.model;

import java.time.Instant;

/** What a workspace's ceilings are counted over: its staff seats, and its billing month. */
public record FairUseAllowance(long staffSeats, Instant periodStart, Instant periodEnd) {}
