package app.lightmove.api.billing.usage.model;

import java.time.Instant;

/** One kind's ceiling this period, what is used of it, and when the period resets. */
public record FairUseHeadroom(long ceiling, long used, Instant periodEnd) {}
