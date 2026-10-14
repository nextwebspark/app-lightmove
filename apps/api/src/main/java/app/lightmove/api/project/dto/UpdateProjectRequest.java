package app.lightmove.api.project.dto;

import app.lightmove.api.core.validation.PlausibleDate;
import java.time.LocalDate;

/** The one project field editable after creation. */
public record UpdateProjectRequest(
        @PlausibleDate LocalDate targetDate
) {}
