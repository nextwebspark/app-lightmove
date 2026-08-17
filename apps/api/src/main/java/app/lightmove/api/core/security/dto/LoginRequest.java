package app.lightmove.api.core.security.dto;

import app.lightmove.api.core.email.service.EmailAddressNormaliser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Password login credentials. */
public record LoginRequest(
        @JsonDeserialize(converter = EmailAddressNormaliser.class)
        @NotBlank(message = "Enter your email")
        @Email(message = "That doesn't look like a valid email")
        String email,

        @NotBlank(message = "Enter your password")
        String password
) {}
