package app.lightmove.api.billing.usage.model;

import java.time.Instant;

/** The span a ceiling is counted over: the subscription's current period, or else the UTC calendar month. */
public record FairUsePeriod(Instant start, Instant end) {}
