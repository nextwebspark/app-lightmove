package app.lightmove.api.core.security.dto;

import app.lightmove.api.core.email.service.EmailAddressNormaliser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Ask for the verification email again. */
public record ResendVerificationRequest(
        @JsonDeserialize(converter = EmailAddressNormaliser.class)
        @NotBlank @Email String email
) {}
