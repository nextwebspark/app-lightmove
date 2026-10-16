package app.uncava.api.candidate.service;

import app.uncava.api.candidate.constant.PersonActivityKind;
import app.uncava.api.candidate.dto.CandidateNextActionDto;
import app.uncava.api.candidate.dto.PipelineRowResponse;
import app.uncava.api.candidate.dto.PipelineStageCountResponse;
import app.uncava.api.candidate.dto.PositionPipelineResponse;
import app.uncava.api.candidate.dto.RankPipelineRowRequest;
import app.uncava.api.candidate.model.Candidate;
import app.uncava.api.candidate.model.CandidateStage;
import app.uncava.api.candidate.model.PipelineCatalog;
import app.uncava.api.candidate.repository.CandidateRepository;
import app.uncava.api.core.audit.constant.ProjectEventType;
import app.uncava.api.core.audit.service.AuditService;
import app.uncava.api.core.error.constant.ErrorCode;
import app.uncava.api.core.error.model.ApiException;
import app.uncava.api.project.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A position's Pipeline page: every executive by stage and then the team's own order, the drag that sets
 * that order, the next action (the earliest open task, {@link NextActionLookup}) and whether a client seat
 * sees them. Staff-only throughout; a stage change is the ordinary status change, to the stage's default status.
 */
@Service
@RequiredArgsConstructor
public class PositionPipelineService {

    /** The gap left between rows ranked in one go, so later drags have room to land between them. */
    private static final BigDecimal RANK_STEP = BigDecimal.valueOf(1024);

    /** Below this a gap is spent: the stage is ranked whole again rather than halved further. */
    private static final BigDecimal MIN_RANK_GAP = new BigDecimal("0.001");

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private static final Comparator<Candidate> STAGE_ORDER = Comparator
            .comparing(Candidate::getPipelineRank, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Candidate::getCreatedAt)
            .thenComparing(Candidate::getId);

    private static final Comparator<Candidate> PIPELINE_ORDER = Comparator
            .comparingInt((Candidate row) -> row.getStatus().getStage().getPosition())
            .thenComparing(STAGE_ORDER);

    private final CandidateRepository candidates;
    private final ProjectRepository projects;
    private final CandidatePipelineCatalog pipeline;
    private final PersonActivityRecorder activity;
    private final NextActionLookup nextActions;
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
        Map<UUID, CandidateNextActionDto> next = nextActionsOf(projectId, shown);
        return new PositionPipelineResponse(shown.stream().map(row -> toRow(row, next)).toList(), counts,
                shown.size());
    }

    /**
     * The stage's rows are locked first, so two drags queue, and the row lands beside {@code after} as the
     * stage now stands rather than as the caller last read it: two rows dropped into one gap never share
     * a rank. A stage never dragged in, or whose gap has halved away, is ranked whole.
     */
    @Transactional
    public PipelineRowResponse rank(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                    RankPipelineRowRequest request, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        UUID stageId = candidates.findStageIdByIdAndProjectId(candidateId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        List<Candidate> stage = candidates.lockStageOfProject(projectId, stageId).stream()
                .sorted(STAGE_ORDER)
                .collect(Collectors.toCollection(ArrayList::new));
        Candidate moved = rowOf(stage, candidateId);
        if (stage.stream().anyMatch(row -> row.getPipelineRank() == null)) {
            rankWhole(stage);
        }
        stage.remove(moved);
        int slot = slotFor(stage, request, candidateId);
        BigDecimal low = slot == 0 ? null : stage.get(slot - 1).getPipelineRank();
        BigDecimal high = slot == stage.size() ? null : stage.get(slot).getPipelineRank();
        if (low != null && high != null && high.subtract(low).compareTo(MIN_RANK_GAP) < 0) {
            stage.add(slot, moved);
            rankWhole(stage);
        } else {
            moved.rankAt(between(low, high));
        }
        audit.projectEvent(ProjectEventType.CANDIDATE_PIPELINE_REORDERED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .record();
        return toRow(moved, nextActionsOf(projectId, List.of(moved)));
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
        return toRow(candidate, nextActionsOf(projectId, List.of(candidate)));
    }

    /** Where the moved row goes among the others: after {@code after}, else before {@code before}, else last. */
    private static int slotFor(List<Candidate> others, RankPipelineRowRequest request, UUID movedId) {
        Candidate after = neighbour(others, request.afterCandidateId(), movedId);
        Candidate before = neighbour(others, request.beforeCandidateId(), movedId);
        if (after != null && before != null && others.indexOf(after) >= others.indexOf(before)) {
            throw ApiException.of(ErrorCode.PIPELINE_ORDER_INVALID);
        }
        if (after != null) {
            return others.indexOf(after) + 1;
        }
        return before == null ? others.size() : others.indexOf(before);
    }

    private static Candidate neighbour(List<Candidate> stage, UUID neighbourId, UUID movedId) {
        if (neighbourId == null) {
            return null;
        }
        if (neighbourId.equals(movedId)) {
            throw ApiException.of(ErrorCode.PIPELINE_ORDER_INVALID);
        }
        return rowOf(stage, neighbourId);
    }

    /** A row that left the stage since it was read is refused like any id outside it. */
    private static Candidate rowOf(List<Candidate> stage, UUID candidateId) {
        return stage.stream().filter(row -> row.getId().equals(candidateId)).findFirst()
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

    private Map<UUID, CandidateNextActionDto> nextActionsOf(UUID projectId, List<Candidate> rows) {
        return rows.isEmpty() ? Map.of()
                : nextActions.nextActionsOf(projectId, rows.stream().map(Candidate::getId).toList());
    }

    private static PipelineRowResponse toRow(Candidate row, Map<UUID, CandidateNextActionDto> next) {
        return new PipelineRowResponse(row.getId(), row.getPerson().getId(), row.getPerson().getFullName(),
                row.getPerson().getTitle(), row.getCompanyName(), row.getTriageCompanyId(),
                row.getStatus().getStatusKey(), row.getStatus().getLabel(), row.getStatus().getColour().value(),
                CandidateResponseMapper.stageOf(row.getStatus()), row.getCreatedAt(), row.getStatusChangedAt(),
                next.get(row.getId()),
                row.isClientVisible(), row.getPerson().getProfile().enrichedAt());
    }
}
