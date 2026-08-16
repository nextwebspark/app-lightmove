package app.lightmove.api.project.dto;

import jakarta.validation.constraints.NotBlank;

/** A company chosen from the universe — its rebuild-stable key. Null on the request means custom. */
public record CompanyPickDto(
        @NotBlank(message = "Choose a company from the database")
        String source,

        @NotBlank(message = "Choose a company from the database")
        String sourceId
) {}
