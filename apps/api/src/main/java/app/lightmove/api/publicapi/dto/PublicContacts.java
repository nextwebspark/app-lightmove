package app.lightmove.api.publicapi.dto;

import app.lightmove.api.candidate.dto.CandidateContactsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "Contacts", description = "Every email and phone known for an executive")
public record PublicContacts(
        @Schema(description = "Email addresses") List<PublicEmail> emails,
        @Schema(description = "Phone numbers") List<PublicPhone> phones
) {

    public static PublicContacts of(CandidateContactsDto contacts) {
        return new PublicContacts(
                contacts.emails().stream()
                        .map(email -> new PublicEmail(email.address(), email.kind(), email.verified()))
                        .toList(),
                contacts.phones().stream()
                        .map(phone -> new PublicPhone(phone.number(), phone.kind(), phone.verified()))
                        .toList());
    }
}
