package app.uncava.api.project.dto;

import app.uncava.api.core.validation.PlausibleDate;
import java.time.LocalDate;

/** The one project field editable after creation. */
public record UpdateProjectRequest(
        @PlausibleDate LocalDate targetDate
) {}
