package app.lightmove.api.billing.credit.model;

import java.util.UUID;

/** What one member's captured finds cost over a period; a spend no member made has a null {@code userId}. */
public record MemberCreditSpend(UUID userId, String name, long emailsFound, long phonesFound, long creditsSpent) {
}
