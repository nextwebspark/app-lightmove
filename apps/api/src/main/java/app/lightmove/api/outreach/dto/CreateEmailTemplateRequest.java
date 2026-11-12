package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A new email template; {@code shared} left out is personal. */
public record CreateEmailTemplateRequest(
        @NotBlank(message = "Name this template") @Size(max = 80, message = "That name is too long") String name,
        @Size(max = 200, message = "That subject is too long") String subject,
        @NotBlank(message = "Write the email") @Size(max = 10000, message = "That email is too long") String body,
        Boolean shared) {

    public boolean isShared() {
        return Boolean.TRUE.equals(shared);
    }
}
