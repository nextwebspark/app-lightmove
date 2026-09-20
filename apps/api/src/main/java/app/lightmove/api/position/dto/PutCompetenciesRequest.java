package app.lightmove.api.position.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Snapshot PUT of both competency lists at once — the screen edits them as one section. The share is
 * optional: absent, the stored split stands.
 */
public record PutCompetenciesRequest(
        @NotNull
        @Size(max = 10, message = "That is too many competencies")
        List<@Valid CompetencyDto> technical,

        @NotNull
        @Size(max = 10, message = "That is too many competencies")
        List<@Valid CompetencyDto> behavioural,

        @Min(value = 0, message = "The technical share is between 0 and 100")
        @Max(value = 100, message = "The technical share is between 0 and 100")
        Integer technicalShare
) {}
