package app.lightmove.api.project.dto;

import java.time.LocalDate;

/** The one project field editable after creation. */
public record UpdateProjectRequest(
        LocalDate targetDate
) {}
