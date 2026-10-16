package app.uncava.api.billing.overview.dto;

import app.uncava.api.billing.credit.model.MemberCreditSpend;
import java.time.Instant;
import java.util.List;

/** This billing month's contact credits spent, per member, most first. */
public record BillingUsageResponse(Instant periodStart, Instant periodEnd, List<MemberCreditSpend> members) {
}
