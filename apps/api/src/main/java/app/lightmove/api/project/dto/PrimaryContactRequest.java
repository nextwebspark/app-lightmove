package app.lightmove.api.project.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The first representative, created with the client. All three fields required to send the invite. */
public record PrimaryContactRequest(
        @NotBlank(message = "Enter the contact's name")
        @Size(max = 160, message = "That name is too long")
        String fullName,

        @Size(max = 160, message = "That position is too long")
        String position,

        @NotBlank(message = "Enter a work email")
        @Email(message = "Enter a valid work email address")
        @Size(max = 320, message = "That email is too long")
        String email
) {}
