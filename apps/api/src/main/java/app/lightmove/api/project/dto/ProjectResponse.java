package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.ProjectHealth;
import app.lightmove.api.project.constant.ProjectStage;
import app.lightmove.api.project.constant.ProjectType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The project as the HTTP contract returns it. {@code companies} is the mandate's live universe —
 * every triaged company it has not declined — and {@code candidates} every executive it has mapped.
 *
 * <p>{@code targetDate} is the brief's target start and nothing else. The dates health is measured
 * against are the two milestones.
 */
public record ProjectResponse(
        UUID id,
        UUID clientId,
        String clientName,
        String clientLogoUrl,
        String positionTitle,
        ProjectStage stage,
        ProjectType projectType,
        ProjectHealth health,
        LocalDate startDate,
        LocalDate mappingTargetDate,
        LocalDate shortlistTargetDate,
        LocalDate targetDate,
        MandateProgressDto progress,
        List<TeamMemberResponse> team,
        List<AttachedRepresentativeResponse> representatives,
        long companies,
        long candidates,
        Instant createdAt
) {}
