package app.lightmove.api.project.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Name a further representative on an existing client and send their portal invite. */
public record InviteRepresentativeRequest(
        @NotBlank(message = "Enter the representative's name")
        @Size(max = 160, message = "That name is too long")
        String fullName,

        @Size(max = 160, message = "That position is too long")
        String position,

        @NotBlank(message = "Enter a work email")
        @Email(message = "Enter a valid work email address")
        @Size(max = 320, message = "That email is too long")
        String email
) {}
