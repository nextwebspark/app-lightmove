package app.lightmove.api.core.security.dto;

import app.lightmove.api.core.email.service.EmailAddressNormaliser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Which organisation signs this address in. */
public record SsoDiscoverRequest(
        @JsonDeserialize(converter = EmailAddressNormaliser.class)
        @NotBlank(message = "Enter your work email")
        @Email(message = "That doesn't look like a valid email")
        String email
) {}
