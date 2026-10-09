package app.lightmove.api.billing.overview.dto;

import app.lightmove.api.billing.credit.constant.ContactCreditLevel;
import java.time.Instant;

/**
 * The workspace's contact credits: {@code monthly} is the plan's grant for this billing month and
 * {@code usedPercent} how much of it is gone; {@code left} is everything spendable now, of which {@code bought} was
 * purchased and {@code given} granted free.
 */
public record ContactCreditsResponse(long monthly, long left, long bought, long given, long usedPercent,
                                     ContactCreditLevel level, Instant resetsAt) {
}
