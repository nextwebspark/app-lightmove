package app.lightmove.api.billing.usage.model;

import java.time.Instant;

/**
 * What a workspace's ceilings are counted over: its staff seats, and the span — the subscription's current period,
 * or else the UTC calendar month.
 */
public record FairUseAllowance(long staffSeats, Instant periodStart, Instant periodEnd) {}
