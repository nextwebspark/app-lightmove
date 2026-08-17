package app.lightmove.api.core.security.dto;

import app.lightmove.api.core.email.service.EmailAddressNormaliser;
import app.lightmove.api.core.security.service.PasswordPolicy;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Signup step 1. */
public record SignupRequest(
        @NotBlank(message = "Enter your full name")
        @Size(max = 160, message = "That name is too long")
        String fullName,

        @JsonDeserialize(converter = EmailAddressNormaliser.class)
        @NotBlank(message = "Enter your work email")
        @Email(message = "That doesn't look like a valid email")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Choose a password")
        @Size(min = PasswordPolicy.MIN_LENGTH, message = "Use at least 8 characters")
        @Pattern(regexp = ".*\\d.*", message = "Include at least one number")
        String password,

        @AssertTrue(message = "You must accept the terms to continue")
        boolean termsAccepted
) {}
