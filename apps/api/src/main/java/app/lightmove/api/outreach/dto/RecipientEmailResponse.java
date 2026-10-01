package app.lightmove.api.outreach.dto;

import app.lightmove.api.candidate.model.RecipientEmail;

/** {@code kind} is "work", "personal" or null — never guessed from the domain. */
public record RecipientEmailResponse(String address, String kind, boolean verified) {

    public static RecipientEmailResponse of(RecipientEmail email) {
        return new RecipientEmailResponse(email.address(), email.kind() == null ? null : email.kind().value(),
                email.verified());
    }
}
