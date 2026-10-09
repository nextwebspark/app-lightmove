package app.lightmove.api.billing.plan.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * The month a workspace's contact credits and fair-use ceilings run over: monthly steps from its subscription's
 * period start, so a yearly plan still resets each month, or the UTC calendar month without one. Each step is
 * counted from the anchor, never from the step before, so a period starting on the 31st keeps returning to it.
 */
public record BillingMonth(Instant start, Instant end) {

    /** @param subscription null for a workspace without one */
    public static BillingMonth of(WorkspaceSubscription subscription, Instant now) {
        return containing(subscription == null ? null : subscription.getCurrentPeriodStart(), now);
    }

    public static BillingMonth containing(Instant anchor, Instant now) {
        ZonedDateTime at = now.atZone(ZoneOffset.UTC);
        ZonedDateTime from = anchor == null
                ? at.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)
                : anchor.atZone(ZoneOffset.UTC);
        long steps = ChronoUnit.MONTHS.between(from, at);
        if (from.plusMonths(steps).isAfter(at)) {
            steps--;
        }
        if (!at.isBefore(from.plusMonths(steps + 1))) {
            steps++;
        }
        return new BillingMonth(from.plusMonths(steps).toInstant(), from.plusMonths(steps + 1).toInstant());
    }
}
