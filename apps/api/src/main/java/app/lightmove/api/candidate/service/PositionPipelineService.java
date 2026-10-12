package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.dto.CandidateNextActionDto;
import app.lightmove.api.candidate.dto.PipelineRowResponse;
import app.lightmove.api.candidate.dto.PipelineStageCountResponse;
import app.lightmove.api.candidate.dto.PositionPipelineResponse;
import app.lightmove.api.candidate.dto.RankPipelineRowRequest;
import app.lightmove.api.candidate.dto.SetNextActionRequest;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateStage;
import app.lightmove.api.candidate.model.PipelineCatalog;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A position's Pipeline page: every executive by stage and then the team's own order, the drag that sets
 * that order, the next action and whether a client seat sees them. Staff-only throughout; a stage change is
 * the ordinary status change, to the stage's default status.
 */
@Service
@RequiredArgsConstructor
public class PositionPipelineService {

    /** The gap left between rows ranked in one go, so later drags have room to land between them. */
    private static final BigDecimal RANK_STEP = BigDecimal.valueOf(1024);

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private static final Comparator<Candidate> PIPELINE_ORDER = Comparator
            .comparingInt((Candidate row) -> row.getStatus().getStage().getPosition())
            .thenComparing(Candidate::getPipelineRank, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Candidate::getCreatedAt)
            .thenComparing(Candidate::getId);

    private final CandidateRepository candidates;
    private final ProjectRepository projects;
    private final CandidatePipelineCatalog pipeline;
    private final PersonActivityRecorder activity;
    private final UserRepository users;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PositionPipelineResponse pipelineOf(UUID workspaceId, UUID projectId, String query, String stageKey) {
        projects.requireInWorkspace(projectId, workspaceId);
        PipelineCatalog catalog = pipeline.catalogOf(workspaceId);
        if (stageKey != null && !stageKey.isBlank() && catalog.stageByKey(stageKey.strip()).isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown pipeline stage: " + stageKey);
        }
        List<Candidate> matching = candidates.findPipelineOfProject(projectId).stream()
                .filter(row -> matches(row, query))
                .sorted(PIPELINE_ORDER)
                .toList();
        Map<String, Long> perStage = matching.stream()
                .collect(Collectors.groupingBy(row -> row.getStatus().getStage().getStageKey(), Collectors.counting()));
        List<PipelineStageCountResponse> counts = catalog.stages().stream()
                .filter(stage -> !stage.isRetired() || perStage.containsKey(stage.getStageKey()))
                .map(stage -> countOf(stage, perStage.getOrDefault(stage.getStageKey(), 0L)))
                .toList();
        List<Candidate> shown = stageKey == null || stageKey.isBlank() ? matching : matching.stream()
                .filter(row -> row.getStatus().getStage().getStageKey().equals(stageKey.strip()))
                .toList();
        Map<UUID, String> setters = namesOf(shown);
        return new PositionPipelineResponse(shown.stream().map(row -> toRow(row, setters)).toList(), counts,
                shown.size());
    }

    /**
     * Places a row between its new neighbours, both of its own stage. A stage nobody has dragged in yet is
     * ranked whole first, in the order it was showing, so every later drag writes the one row moved.
     */
    @Transactional
    public PipelineRowResponse rank(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                    RankPipelineRowRequest request, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate moved = candidates.requireInProject(candidateId, projectId);
        UUID stageId = moved.getStatus().getStage().getId();
        List<Candidate> stage = candidates.findPipelineOfProject(projectId).stream()
                .filter(row -> row.getStatus().getStage().getId().equals(stageId))
                .sorted(PIPELINE_ORDER)
                .toList();
        Candidate after = neighbour(stage, request.afterCandidateId(), candidateId);
        Candidate before = neighbour(stage, request.beforeCandidateId(), candidateId);
        if (stage.stream().anyMatch(row -> row.getPipelineRank() == null)) {
            rankWhole(stage);
        }
        BigDecimal low = after == null ? null : after.getPipelineRank();
        BigDecimal high = before == null ? null : before.getPipelineRank();
        if (low != null && high != null && low.compareTo(high) >= 0) {
            throw ApiException.of(ErrorCode.PIPELINE_ORDER_INVALID);
        }
        moved.rankAt(between(low, high));
        audit.projectEvent(ProjectEventType.CANDIDATE_PIPELINE_REORDERED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .record();
        return toRow(moved, namesOf(List.of(moved)));
    }

    @Transactional
    public PipelineRowResponse setNextAction(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                             SetNextActionRequest request, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        candidate.planNextAction(request.text(), request.dueAt(), userId);
        boolean cleared = candidate.getNextAction() == null;
        activity.record(candidate, userId, PersonActivityKind.NEXT_ACTION_SET, cleared
                ? PersonActivityDetails.of("cleared", true)
                : PersonActivityDetails.of("text", candidate.getNextAction()).and("dueAt", dueOf(candidate)));
        audit.projectEvent(ProjectEventType.CANDIDATE_NEXT_ACTION_SET, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("cleared", cleared)
                .record();
        return toRow(candidate, namesOf(List.of(candidate)));
    }

    @Transactional
    public PipelineRowResponse setClientVisibility(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                                   boolean visible, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        if (candidate.showToClient(visible)) {
            activity.record(candidate, userId, PersonActivityKind.CLIENT_VISIBILITY_CHANGED,
                    PersonActivityDetails.of("visible", visible));
            audit.projectEvent(ProjectEventType.CANDIDATE_CLIENT_VISIBILITY_CHANGED, userId, workspaceId, projectId,
                            httpRequest)
                    .detail("candidateId", candidateId.toString())
                    .detail("visible", visible)
                    .record();
        }
        return toRow(candidate, namesOf(List.of(candidate)));
    }

    private static String dueOf(Candidate candidate) {
        return candidate.getNextActionDue() == null ? null : candidate.getNextActionDue().toString();
    }

    private static Candidate neighbour(List<Candidate> stage, UUID neighbourId, UUID movedId) {
        if (neighbourId == null) {
            return null;
        }
        if (neighbourId.equals(movedId)) {
            throw ApiException.of(ErrorCode.PIPELINE_ORDER_INVALID);
        }
        return stage.stream().filter(row -> row.getId().equals(neighbourId)).findFirst()
                .orElseThrow(() -> ApiException.of(ErrorCode.PIPELINE_ORDER_INVALID));
    }

    private static void rankWhole(List<Candidate> stage) {
        BigDecimal rank = RANK_STEP;
        for (Candidate row : stage) {
            row.rankAt(rank);
            rank = rank.add(RANK_STEP);
        }
    }

    static BigDecimal between(BigDecimal low, BigDecimal high) {
        if (low == null && high == null) {
            return RANK_STEP;
        }
        if (low == null) {
            return high.subtract(RANK_STEP);
        }
        if (high == null) {
            return low.add(RANK_STEP);
        }
        return low.add(high).divide(TWO, MathContext.DECIMAL128);
    }

    private static boolean matches(Candidate row, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return contains(row.getPerson().getFullName(), needle) || contains(row.getPerson().getTitle(), needle)
                || contains(row.getCompanyName(), needle);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static PipelineStageCountResponse countOf(CandidateStage stage, long count) {
        return new PipelineStageCountResponse(stage.getStageKey(), stage.getLabel(), stage.getColour().value(),
                stage.getKind().value(), stage.getPosition(), count);
    }

    private Map<UUID, String> namesOf(List<Candidate> rows) {
        List<UUID> ids = rows.stream().map(Candidate::getNextActionSetBy).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : users.findAllById(ids).stream()
                .filter(user -> user.getFullName() != null)
                .collect(Collectors.toMap(User::getId, User::getFullName, (first, second) -> first));
    }

    private static PipelineRowResponse toRow(Candidate row, Map<UUID, String> setters) {
        CandidateNextActionDto next = row.getNextAction() == null ? null
                : new CandidateNextActionDto(row.getNextAction(), row.getNextActionDue(),
                        setters.get(row.getNextActionSetBy()));
        return new PipelineRowResponse(row.getId(), row.getPerson().getId(), row.getPerson().getFullName(),
                row.getPerson().getTitle(), row.getCompanyName(), row.getTriageCompanyId(),
                row.getStatus().getStatusKey(), row.getStatus().getLabel(), row.getStatus().getColour().value(),
                CandidateResponseMapper.stageOf(row.getStatus()), row.getCreatedAt(), row.getStatusChangedAt(), next,
                row.isClientVisible(), row.getPerson().getProfile().enrichedAt());
    }
}
