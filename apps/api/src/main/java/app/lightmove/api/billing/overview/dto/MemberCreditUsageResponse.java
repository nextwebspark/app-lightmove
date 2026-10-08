package app.lightmove.api.billing.overview.dto;

import java.util.UUID;

/** {@code userId} and {@code name} are null for credits no member spent. */
public record MemberCreditUsageResponse(UUID userId, String name, long emailsFound, long phonesFound,
                                        long creditsSpent) {
}
