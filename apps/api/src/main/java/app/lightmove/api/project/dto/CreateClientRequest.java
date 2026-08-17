package app.lightmove.api.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Create a client. Either {@code company} names a universe row (its canonical name/domain/hq are
 * resolved server-side rather than trusted from the request), or {@code customName} types a new
 * record in. {@code sector}/{@code hqCountry} are editable regardless. An optional
 * {@code primaryContact} gets a portal invite immediately.
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
