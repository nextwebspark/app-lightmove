package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.ProjectHealth;
import app.lightmove.api.project.constant.ProjectStage;
import app.lightmove.api.project.constant.ProjectType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code companies} excludes declined ones and {@code candidates} those out of the running;
 * {@code mappedCandidates} is everyone ever mapped, {@code mappedCompanies} the coverage bar's count.
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
