package app.lightmove.api.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A company chosen from the universe — its Apollo account id, which is the identity every stored
 * reference uses. Null on the request means the client is naming a company the universe does not
 * carry, and the record is created custom instead.
 */
public record CompanyPickDto(
        @NotBlank(message = "Choose a company from the database")
        @Size(max = 64)
        String apolloAccountId
) {}
