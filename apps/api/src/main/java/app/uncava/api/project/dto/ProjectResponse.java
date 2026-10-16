package app.uncava.api.project.dto;

import app.uncava.api.project.constant.ProjectHealth;
import app.uncava.api.project.constant.ProjectStage;
import app.uncava.api.project.constant.ProjectType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code companies} excludes declined ones and {@code candidates} those out of the running;
 * {@code mappedCandidates} is everyone ever mapped, {@code mappedCompanies} the coverage bar's count.
 * {@code archivedAt} is set on a position put away; the list carries those only when asked for them.
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
        Instant createdAt,
        Instant archivedAt,
        String archivedByName
) {}
