package app.lightmove.api.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Snapshot PUT of both competency lists at once — the screen edits them as one section. */
public record PutCompetenciesRequest(
        @NotNull
        @Size(max = 10, message = "That is too many competencies")
        List<@Valid CompetencyDto> technical,

        @NotNull
        @Size(max = 10, message = "That is too many competencies")
        List<@Valid CompetencyDto> behavioural
) {}
