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
 * every triaged company it has not declined — and {@code candidates} every executive still in the
 * running, {@code mappedCandidates} every executive ever mapped. {@code mappedCompanies} is how many
 * of those companies have anyone mapped at them, the side panel's coverage bar;
 * {@code engagedCandidates} those who have answered.
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
        ProjectType projectType,
        LocalDate startDate,
        LocalDate deliveryDate,
        LocalDate mappingTargetDate,
        List<TeamMemberResponse> team,
        List<AttachedRepresentativeResponse> representatives,
        long companies,
        long candidates,
        long mappedCandidates,
        long engagedCandidates,
        long mappedCompanies,
        Instant createdAt
) {}
