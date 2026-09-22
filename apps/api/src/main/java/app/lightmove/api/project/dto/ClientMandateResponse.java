package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.ProjectHealth;
import app.lightmove.api.project.constant.ProjectStage;
import app.lightmove.api.project.constant.ProjectType;
import java.time.LocalDate;
import java.util.UUID;

/** A mandate as the client drawer lists it — enough to render the row and open the project. */
public record ClientMandateResponse(
        UUID id,
        String positionTitle,
        ProjectStage stage,
        ProjectType projectType,
        ProjectHealth health,
        String leadName,
        LocalDate mappingTargetDate,
        LocalDate shortlistTargetDate,
        LocalDate targetDate
) {}
