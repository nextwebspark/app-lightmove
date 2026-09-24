package app.lightmove.api.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Edit a client record's own fields; mandates and representatives have their own endpoints. A field
 * left out is unchanged and a blank one is cleared — only the name is required.
 */
public record UpdateClientRequest(
        @NotBlank(message = "Enter the business unit name")
        @Size(max = 160, message = "That name is too long")
        String name,

        @Size(max = 96, message = "That sector is too long")
        String sector,

        @Size(max = 64, message = "That location is too long")
        String hqCountry,

        @Size(max = 160, message = "That domain is too long")
        String domain,

        String offLimitsNote,

        @Size(max = 2000, message = "Those notes are too long")
        String notes
) {}
