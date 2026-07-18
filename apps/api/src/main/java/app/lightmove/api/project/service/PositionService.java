package app.lightmove.api.project.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.CompetencyPanel;
import app.lightmove.api.project.constant.CriterionMode;
import app.lightmove.api.project.constant.EmploymentType;
import app.lightmove.api.project.dto.PositionDtos.CompetencyDto;
import app.lightmove.api.project.dto.PositionDtos.CriterionResponse;
import app.lightmove.api.project.dto.PositionDtos.PositionResponse;
import app.lightmove.api.project.dto.PositionDtos.PutCompetenciesRequest;
import app.lightmove.api.project.dto.PositionDtos.PutCriteriaRequest;
import app.lightmove.api.project.dto.PositionDtos.UpdatePositionRequest;
import app.lightmove.api.project.model.CompetencyRow;
import app.lightmove.api.project.model.Position;
import app.lightmove.api.project.model.PositionCriterion;
import app.lightmove.api.project.model.PositionDetails;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.PositionRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.project.service.PositionTemplates.TemplateSeed;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The position brief behind a project. Seeded from {@link PositionTemplates} when the project is
 * created (and lazily on first read, for projects that predate the position tables). Every load is
 * scoped through the project's {@code (id, workspaceId)} lookup — the workspace id comes from the
 * principal, so a foreign project 404s before any position row is touched.
 *
 * <p>Locking freezes the whole brief: every write rejects with {@code POSITION_LOCKED} until a
 * project admin unlocks. Readiness (both panels exactly 100%, at least one required criterion) is
 * validated only at lock time — autosave must be free to persist half-balanced panels.
 */
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positions;
    private final ProjectRepository projects;
    private final ClientRepository clients;
    private final AuditService audit;

    @Transactional
    public PositionResponse get(UUID workspaceId, UUID projectId) {
        return toResponse(loadBrief(projectId, workspaceId));
    }

    @Transactional
    public PositionResponse update(UUID userId, UUID workspaceId, UUID projectId,
                                   UpdatePositionRequest request, HttpServletRequest httpRequest) {
        Brief brief = loadUnlockedBrief(projectId, workspaceId);
        brief.position().apply(new PositionDetails(
                request.mandateReason(), request.internalContext(), request.narrative(),
                request.reportsTo(), request.directReports(), request.teamSize(),
                request.location(), request.employmentType(),
                request.salaryMin(), request.salaryMax(), request.currency(),
                request.noticeValue(), request.noticeUnit(), request.bonusTargetPct(), request.ltip(),
                request.benefits() == null ? List.of() : request.benefits(),
                request.confidential()));
        // The mandate's one target date lives on the project — the screen's "Target start" writes here.
        brief.project().setTargetDate(request.startTarget());
        auditPositionChange(userId, workspaceId, projectId, "details", httpRequest);
        return toResponse(brief);
    }

    @Transactional
    public PositionResponse putCriteria(UUID userId, UUID workspaceId, UUID projectId,
                                        PutCriteriaRequest request, HttpServletRequest httpRequest) {
        Brief brief = loadUnlockedBrief(projectId, workspaceId);
        brief.position().replaceCriteria(request.criteria().stream()
                .map(c -> PositionCriterion.of(c.text(), c.mode(), c.fromBrief()))
                .toList());
        auditPositionChange(userId, workspaceId, projectId, "criteria", httpRequest);
        return toResponse(brief);
    }

    @Transactional
    public PositionResponse putCompetencies(UUID userId, UUID workspaceId, UUID projectId,
                                            PutCompetenciesRequest request, HttpServletRequest httpRequest) {
        Brief brief = loadUnlockedBrief(projectId, workspaceId);
        brief.position().replaceCompetencies(Stream.concat(
                        request.technical().stream()
                                .map(c -> CompetencyRow.of(CompetencyPanel.TECHNICAL, c.name(), c.weight())),
                        request.behavioural().stream()
                                .map(c -> CompetencyRow.of(CompetencyPanel.BEHAVIOURAL, c.name(), c.weight())))
                .toList());
        auditPositionChange(userId, workspaceId, projectId, "competencies", httpRequest);
        return toResponse(brief);
    }

    @Transactional
    public PositionResponse lock(UUID userId, UUID workspaceId, UUID projectId,
                                 HttpServletRequest httpRequest) {
        Brief brief = loadBrief(projectId, workspaceId);
        if (brief.position().isLocked()) {
            throw ApiException.of(ErrorCode.POSITION_LOCKED);
        }
        requireReady(brief.position());
        brief.position().lock(userId, Instant.now());
        audit.event(ProjectEventType.POSITION_LOCKED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .record();
        return toResponse(brief);
    }

    @Transactional
    public PositionResponse unlock(UUID userId, UUID workspaceId, UUID projectId,
                                   HttpServletRequest httpRequest) {
        Brief brief = loadBrief(projectId, workspaceId);
        if (!brief.position().isLocked()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This position is not locked");
        }
        brief.position().unlock();
        audit.event(ProjectEventType.POSITION_UNLOCKED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .record();
        return toResponse(brief);
    }

    /**
     * Seeds the brief from the template matched on the project's position title, prefilled with the
     * client's HQ country as the location. Called by {@code ProjectService.create} and by the lazy
     * path in {@link #get} for pre-V7 projects.
     */
    @Transactional
    public Position seedFor(Project project) {
        TemplateSeed seed = PositionTemplates.forTitle(project.getPositionTitle());
        String location = clients.findByIdAndWorkspaceId(project.getClientId(), project.getWorkspaceId())
                .map(client -> client.getHqCountry())
                .orElse(null);

        Position position = Position.forProject(project.getId());
        position.apply(new PositionDetails(
                position.getMandateReason(), null, seed.narrative(),
                seed.reportsTo(), null, null,
                location, EmploymentType.FULL_TIME_PERMANENT,
                null, null, "USD",
                null, null, null, null,
                List.of(), false));
        position.replaceCriteria(seed.criteria());
        position.replaceCompetencies(seed.competencies());
        return positions.save(position);
    }

    /** The lock gate — the only place totals are enforced, so autosave stays permissive. */
    private void requireReady(Position position) {
        boolean technicalBalanced = panelTotal(position, CompetencyPanel.TECHNICAL) == 100;
        boolean behaviouralBalanced = panelTotal(position, CompetencyPanel.BEHAVIOURAL) == 100;
        boolean hasRequired = position.getCriteria().stream()
                .anyMatch(criterion -> criterion.getMode() == CriterionMode.REQUIRED);
        if (!technicalBalanced || !behaviouralBalanced || !hasRequired) {
            throw ApiException.of(ErrorCode.POSITION_NOT_READY);
        }
    }

    private static int panelTotal(Position position, CompetencyPanel panel) {
        return position.getCompetencies().stream()
                .filter(row -> row.getPanel() == panel)
                .mapToInt(CompetencyRow::getWeight)
                .sum();
    }

    private Brief loadUnlockedBrief(UUID projectId, UUID workspaceId) {
        Brief brief = loadBrief(projectId, workspaceId);
        if (brief.position().isLocked()) {
            throw ApiException.of(ErrorCode.POSITION_LOCKED);
        }
        return brief;
    }

    /** The project and its brief together — the target date lives on the project, so both are loaded. */
    private Brief loadBrief(UUID projectId, UUID workspaceId) {
        Project project = requireProject(projectId, workspaceId);
        Position position = positions.findByProjectId(project.getId())
                .orElseGet(() -> seedFor(project));
        return new Brief(project, position);
    }

    private Project requireProject(UUID projectId, UUID workspaceId) {
        return projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private record Brief(Project project, Position position) {
    }

    private void auditPositionChange(UUID userId, UUID workspaceId, UUID projectId,
                                     String section, HttpServletRequest httpRequest) {
        audit.event(ProjectEventType.POSITION_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("section", section)
                .record();
    }

    private PositionResponse toResponse(Brief brief) {
        Position position = brief.position();
        return new PositionResponse(
                position.getMandateReason(), position.getInternalContext(), position.getNarrative(),
                position.getReportsTo(), position.getDirectReports(), position.getTeamSize(),
                position.getLocation(), position.getEmploymentType(), brief.project().getTargetDate(),
                position.getSalaryMin(), position.getSalaryMax(), position.getCurrency(),
                position.getNoticeValue(), position.getNoticeUnit(), position.getBonusTargetPct(),
                position.getLtip(),
                List.copyOf(position.getBenefits()),
                position.isConfidential(),
                position.getCriteria().stream()
                        .map(c -> new CriterionResponse(c.getText(), c.getMode(), c.isFromBrief()))
                        .toList(),
                panelDtos(position, CompetencyPanel.TECHNICAL),
                panelDtos(position, CompetencyPanel.BEHAVIOURAL),
                position.isLocked(), position.getLockedAt());
    }

    private static List<CompetencyDto> panelDtos(Position position, CompetencyPanel panel) {
        return position.getCompetencies().stream()
                .filter(row -> row.getPanel() == panel)
                .map(row -> new CompetencyDto(row.getName(), row.getWeight()))
                .toList();
    }
}
