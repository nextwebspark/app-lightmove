package app.lightmove.api.candidate.dto;

import app.lightmove.api.core.email.service.EmailAddressNormaliser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * The whole card at once: a blank text field clears it. {@code noticeUrl} must be https; {@code defaultLawfulBasis}
 * is a {@code LawfulBasis} wire token. The consent fields (#1267) are optional, so an older client keeps what is held:
 * {@code privacyMode} a {@code CandidatePrivacyMode} token, {@code keepOnFileMonths} 12, 24 or 36,
 * {@code renewalDaysBefore} 0 to 90.
 */
public record UpdateCandidatePrivacySettingsRequest(
        @Size(max = 200) String controllerName,
        @Email @Size(max = 320) @JsonDeserialize(converter = EmailAddressNormaliser.class) String privacyContactEmail,
        @Size(max = 2048) String noticeUrl,
        @NotBlank String defaultLawfulBasis,
        String privacyMode,
        Boolean keepOnFile,
        Integer keepOnFileMonths,
        Boolean shareWithClient,
        Boolean marketing,
        Integer renewalDaysBefore
) {}
