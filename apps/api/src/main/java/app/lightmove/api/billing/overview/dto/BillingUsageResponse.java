package app.lightmove.api.billing.overview.dto;

import app.lightmove.api.billing.credit.model.MemberCreditSpend;
import app.lightmove.api.billing.usage.model.FairUseSpent;
import java.time.Instant;
import java.util.List;

/**
 * This billing month's contact credits spent, per member, most first; and the workspace's recorded notetaker minutes
 * beside their fair-use ceiling (#956).
 */
public record BillingUsageResponse(Instant periodStart, Instant periodEnd, List<MemberCreditSpend> members,
                                   FairUseSpent notetakerMinutes) {
}
