package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.ProjectHealth;
import app.lightmove.api.project.constant.ProjectStage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The project as the HTTP contract returns it. {@code companies} is the mandate's live universe —
 * every triaged company it has not declined — and {@code candidates} every executive it has mapped.
 */
public record ProjectResponse(
        UUID id,
        UUID clientId,
        String clientName,
        String clientLogoUrl,
        String positionTitle,
        ProjectStage stage,
        ProjectHealth health,
        LocalDate targetDate,
        List<TeamMemberResponse> team,
        List<AttachedRepresentativeResponse> representatives,
        long companies,
        long candidates,
        Instant createdAt
) {}
