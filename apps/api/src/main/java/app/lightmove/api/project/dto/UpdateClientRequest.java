package app.lightmove.api.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Edit a client record's own fields; mandates and representatives have their own endpoints. */
public record UpdateClientRequest(
        @NotBlank(message = "Enter the client's name")
        @Size(max = 160, message = "That name is too long")
        String name,

        @Size(max = 96, message = "That sector is too long")
        String sector,

        @Size(max = 64, message = "That location is too long")
        String hqCountry,

        @Size(max = 160, message = "That domain is too long")
        String domain,

        String offLimitsNote
) {}
