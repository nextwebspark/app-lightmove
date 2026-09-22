package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.ProjectType;
import java.time.LocalDate;

/**
 * A patch of the mandate itself. Every field is optional and null means "not supplied": what is sent
 * is merged over what is stored, and the merged timeline is then held to the same rules a create is,
 * so a mandate cannot be edited into a shape it could not have been created in.
 */
public record UpdateProjectRequest(
        LocalDate targetDate,
        ProjectType projectType,
        LocalDate startDate,
        LocalDate mappingTargetDate,
        LocalDate shortlistTargetDate
) {}
