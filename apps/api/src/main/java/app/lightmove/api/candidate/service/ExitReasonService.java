package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.dto.CreateExitReasonRequest;
import app.lightmove.api.candidate.dto.ExitReasonCatalogResponse;
import app.lightmove.api.candidate.dto.ExitReasonResponse;
import app.lightmove.api.candidate.dto.UpdateExitReasonRequest;
import app.lightmove.api.candidate.model.CandidateStatusDefinition;
import app.lightmove.api.candidate.model.ExitReason;
import app.lightmove.api.candidate.repository.ExitReasonRepository;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A workspace's reasons for leaving the process: read by staff, edited by an admin from Settings → Pipeline. A
 * reason is offered only at out-of-process statuses; a status offering none asks for none. Every write answers the
 * whole list and is one audit event.
 */
@Service
@RequiredArgsConstructor
public class ExitReasonService {

    private final ExitReasonRepository reasons;
    private final CandidatePipelineCatalog pipeline;
    private final PipelineEditAudit audit;

    /** Seeds the starting list the first time a workspace whose pipeline came later reads it. */
    @Transactional
    public ExitReasonCatalogResponse catalog(UUID workspaceId) {
        pipeline.catalogOf(workspaceId);
        if (!reasons.existsByWorkspaceId(workspaceId)) {
            reasons.seedIfAbsent(workspaceId);
        }
        return toResponse(reasons.findByWorkspaceIdOrderByPositionAsc(workspaceId));
    }

    @Transactional
    public ExitReasonCatalogResponse create(UUID userId, UUID workspaceId, CreateExitReasonRequest request,
                                            HttpServletRequest httpRequest) {
        List<ExitReason> held = reasons.findByWorkspaceIdOrderByPositionAsc(workspaceId);
        String label = requireLabel(request.label());
        reasons.findByLabel(workspaceId, label).ifPresent(existing -> {
            throw ApiException.of(existing.isRetired() ? ErrorCode.EXIT_REASON_RETIRED : ErrorCode.EXIT_REASON_TAKEN);
        });
        String key = PipelineRules.freeKey(label, "reason",
                candidate -> reasons.existsByWorkspaceIdAndReasonKey(workspaceId, candidate));
        int position = held.stream().mapToInt(ExitReason::getPosition).max().orElse(0) + 1;
        ExitReason reason = ExitReason.added(workspaceId, key, label, Boolean.TRUE.equals(request.requiresNote()),
                position, userId);
        if (request.statusIds() != null) {
            reason.offerAt(requireWaysOut(workspaceId, request.statusIds(), Set.of()));
        }
        reasons.save(reason);
        audit.record(userId, workspaceId, "reasonCreated", reason.getId(), httpRequest);
        return toResponse(reasons.findByWorkspaceIdOrderByPositionAsc(workspaceId));
    }

    @Transactional
    public ExitReasonCatalogResponse update(UUID userId, UUID workspaceId, UUID reasonId,
                                            UpdateExitReasonRequest request, HttpServletRequest httpRequest) {
        ExitReason reason = reasons.requireInWorkspace(reasonId, workspaceId);
        if (request.label() != null) {
            String label = requireLabel(request.label());
            reasons.findByLabel(workspaceId, label)
                    .filter(existing -> !existing.getId().equals(reasonId))
                    .ifPresent(existing -> {
                        throw ApiException.of(ErrorCode.EXIT_REASON_TAKEN);
                    });
            reason.rename(label);
        }
        if (request.requiresNote() != null) {
            reason.askForNote(request.requiresNote());
        }
        if (request.statusIds() != null) {
            reason.offerAt(requireWaysOut(workspaceId, request.statusIds(), reason.getStatusIds()));
        }
        if (Boolean.TRUE.equals(request.retired())) {
            reason.retire();
        } else if (Boolean.FALSE.equals(request.retired())) {
            reason.restore();
        }
        audit.record(userId, workspaceId, "reasonUpdated", reasonId, httpRequest);
        return toResponse(reasons.findByWorkspaceIdOrderByPositionAsc(workspaceId));
    }

    /** {@code ids} names every live reason once; retired ones keep their place behind them. */
    @Transactional
    public ExitReasonCatalogResponse reorder(UUID userId, UUID workspaceId, List<UUID> ids,
                                             HttpServletRequest httpRequest) {
        List<ExitReason> held = reasons.findByWorkspaceIdOrderByPositionAsc(workspaceId);
        List<ExitReason> live = held.stream().filter(reason -> !reason.isRetired()).toList();
        if (!PipelineRules.sameMembers(ids, live.stream().map(ExitReason::getId).toList())) {
            throw ApiException.of(ErrorCode.PIPELINE_ORDER_INVALID);
        }
        Map<UUID, ExitReason> byId = live.stream().collect(Collectors.toMap(ExitReason::getId, Function.identity()));
        int position = 1;
        for (UUID id : ids) {
            byId.get(id).placeAt(position++);
        }
        for (ExitReason retired : held.stream().filter(ExitReason::isRetired).toList()) {
            retired.placeAt(position++);
        }
        audit.record(userId, workspaceId, "reasonsReordered", null, httpRequest);
        return toResponse(reasons.findByWorkspaceIdOrderByPositionAsc(workspaceId));
    }

    private static String requireLabel(String requested) {
        String label = requested.strip();
        if (label.isEmpty()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "label", "A reason needs a name");
        }
        return label;
    }

    /**
     * Every status named must be the workspace's and a way out. A retired one is never newly offered, but one the
     * reason already had stays, so saving the list again never drops it.
     */
    private Set<UUID> requireWaysOut(UUID workspaceId, Collection<UUID> statusIds, Set<UUID> alreadyOffered) {
        Map<UUID, CandidateStatusDefinition> statuses = pipeline.catalogOf(workspaceId).statuses().stream()
                .collect(Collectors.toMap(CandidateStatusDefinition::getId, Function.identity()));
        for (UUID statusId : statusIds) {
            CandidateStatusDefinition status = statuses.get(statusId);
            if (status == null || !status.isOutOfProcess()
                    || (status.isRetired() && !alreadyOffered.contains(statusId))) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "statusIds",
                        "A reason is offered only at live statuses out of process");
            }
        }
        return Set.copyOf(statusIds);
    }

    private static ExitReasonCatalogResponse toResponse(List<ExitReason> held) {
        return new ExitReasonCatalogResponse(held.stream()
                .map(reason -> new ExitReasonResponse(reason.getId(), reason.getReasonKey(), reason.getLabel(),
                        reason.isRequiresNote(), reason.getPosition(), reason.isRetired(),
                        reason.getStatusIds().stream().sorted().toList()))
                .toList());
    }
}
