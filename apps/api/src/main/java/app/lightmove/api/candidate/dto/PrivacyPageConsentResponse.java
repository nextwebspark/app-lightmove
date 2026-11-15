package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One purpose on the public privacy page (#1267): the sentence the person answers, where it stands, and whether it
 * still takes an answer. {@code purpose} and {@code status} are {@code ConsentPurpose} and {@code ConsentStatus} wire
 * tokens. {@code answeredAt} is when a consent in force was given; {@code keepOnFileMonths} how long a yes to keeping
 * them on file lasts. {@code answer} is what was answered on this request's link ({@code given} or {@code declined}),
 * null while unanswered. {@code roleTitle} and {@code clientName} describe a share; the client only when the firm chose
 * to name it.
 */
public record PrivacyPageConsentResponse(
        UUID id,
        String purpose,
        String status,
        boolean open,
        String wording,
        Instant validUntil,
        Instant answeredAt,
        Integer keepOnFileMonths,
        String answer,
        String roleTitle,
        String clientName
) {}
