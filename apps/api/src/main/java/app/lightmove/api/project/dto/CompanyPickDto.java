package app.lightmove.api.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A company chosen from the universe by Apollo account id; null on the request means a custom record. */
public record CompanyPickDto(
        @NotBlank(message = "Choose a company from the database")
        @Size(max = 64)
        String apolloAccountId
) {}
