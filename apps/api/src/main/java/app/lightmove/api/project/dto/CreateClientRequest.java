package app.lightmove.api.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Either {@code company} names a universe row, resolved server-side, or {@code customName} types one
 * in. An optional {@code primaryContact} is invited at once.
 */
public record CreateClientRequest(
        @Valid CompanyPickDto company,

        @Size(max = 160, message = "That name is too long")
        String customName,

        @Size(max = 160, message = "That domain is too long")
        String customDomain,

        @Size(max = 96, message = "That sector is too long")
        String sector,

        @Size(max = 64, message = "That location is too long")
        String hqCountry,

        @Valid PrimaryContactRequest primaryContact
) {}
