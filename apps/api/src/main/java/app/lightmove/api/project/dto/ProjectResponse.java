package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.ProjectHealth;
import app.lightmove.api.project.constant.ProjectStage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The project as the HTTP contract returns it. Pipeline counts are 0 until pipeline tables exist. */
public record ProjectResponse(
        UUID id,
        UUID clientId,
        String clientName,
        String positionTitle,
        ProjectStage stage,
        ProjectHealth health,
        LocalDate targetDate,
        List<TeamMemberResponse> team,
        List<AttachedRepresentativeResponse> representatives,
        int companies,
        int candidates,
        Instant createdAt
) {}
