package app.lightmove.api.billing.overview.dto;

import java.time.Instant;
import java.util.List;

/** This billing month's contact credits spent, per member, most first. */
public record BillingUsageResponse(Instant periodStart, Instant periodEnd, List<MemberCreditUsageResponse> members) {
}
