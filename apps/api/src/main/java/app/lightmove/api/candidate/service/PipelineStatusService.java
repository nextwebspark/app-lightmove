package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.dto.CreatePipelineStatusRequest;
import app.lightmove.api.candidate.dto.PipelineCatalogResponse;
import app.lightmove.api.candidate.dto.UpdatePipelineStatusRequest;
import app.lightmove.api.candidate.model.CandidateStage;
import app.lightmove.api.candidate.model.CandidateStatusDefinition;
import app.lightmove.api.candidate.model.PipelineCatalog;
import app.lightmove.api.candidate.repository.CandidateStageRepository;
import app.lightmove.api.candidate.repository.CandidateStatusDefinitionRepository;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An admin's edits to the pipeline's statuses: add, rename, recolour, move to another stage, make default, take out
 * of process or bring back, retire or restore, reorder. A status always has a stage; a stage's default is always a
 * live status in process, one per stage while it has any; and a live status in process always remains for new people
 * to start at. Every write answers the whole catalog and is one audit event.
 */
@Service
@RequiredArgsConstructor
public class PipelineStatusService {

    private final CandidatePipelineCatalog catalogs;
    private final CandidateStageRepository stages;
    private final CandidateStatusDefinitionRepository statuses;
    private final PipelineDefaultMarks defaults;
    private final PipelineEditAudit audit;

    @Transactional
    public PipelineCatalogResponse createStatus(UUID userId, UUID workspaceId, CreatePipelineStatusRequest request,
                                                HttpServletRequest httpRequest) {
        PipelineCatalog catalog = catalogs.catalogOf(workspaceId);
        CandidateStage stage = stages.requireInWorkspace(request.stageId(), workspaceId);
        PipelineRules.refuseRetiredStage(stage);
        String label = request.label().strip();
        statuses.findByLabel(workspaceId, label).ifPresent(existing -> {
            throw ApiException.of(existing.isRetired() ? ErrorCode.PIPELINE_STATUS_RETIRED : ErrorCode.PIPELINE_LABEL_TAKEN);
        });
        String key = PipelineRules.freeKey(label, "status",
                candidate -> statuses.existsByWorkspaceIdAndStatusKey(workspaceId, candidate));
        boolean outOfProcess = Boolean.TRUE.equals(request.outOfProcess());
        boolean firstInProcess = !outOfProcess && catalog.defaultOf(stage).isEmpty();
        CandidateStatusDefinition status = statuses.save(CandidateStatusDefinition.added(stage, key, label,
                PipelineRules.colourOf(request.colour(), stage.getColour()), PipelineRules.nextPositionIn(catalog, stage),
                firstInProcess, outOfProcess, userId));
        audit.record(userId, workspaceId, "statusCreated", status.getId(), httpRequest);
        return PipelineCatalogMapper.toResponse(catalogs.catalogOf(workspaceId));
    }

    /**
     * Each field left null is left as it is. The flag goes before a move, so a status moved and taken out at once
     * never claims its new stage's default on the way.
     */
    @Transactional
    public PipelineCatalogResponse updateStatus(UUID userId, UUID workspaceId, UUID statusId,
                                                UpdatePipelineStatusRequest request, HttpServletRequest httpRequest) {
        PipelineCatalog catalog = catalogs.catalogOf(workspaceId);
        CandidateStatusDefinition status = statuses.requireInWorkspace(statusId, workspaceId);
        if (request.label() != null) {
            rename(workspaceId, status, request.label());
        }
        if (request.colour() != null) {
            status.recolour(PipelineRules.requireColour(request.colour()));
        }
        if (request.outOfProcess() != null && request.outOfProcess() != status.isOutOfProcess()) {
            flag(catalog, status, request.outOfProcess());
        }
        if (request.stageId() != null && !request.stageId().equals(status.getStage().getId())) {
            move(catalog, status, stages.requireInWorkspace(request.stageId(), workspaceId));
        }
        if (Boolean.TRUE.equals(request.isDefault()) && !status.isDefaultOfStage()) {
            makeDefault(catalog, status);
        }
        if (Boolean.TRUE.equals(request.retired()) && !status.isRetired()) {
            PipelineRules.refuseLeavingNoStart(catalog, List.of(status));
            defaults.retire(catalog, status);
        } else if (Boolean.FALSE.equals(request.retired()) && status.isRetired()) {
            PipelineRules.refuseRetiredStage(status.getStage());
            status.restore();
            defaults.claimIfFree(catalog, status);
        }
        audit.record(userId, workspaceId, "statusUpdated", statusId, httpRequest);
        return PipelineCatalogMapper.toResponse(catalogs.catalogOf(workspaceId));
    }

    /** {@code ids} names every live status of the stage once; retired ones keep their place behind them. */
    @Transactional
    public PipelineCatalogResponse reorderStatuses(UUID userId, UUID workspaceId, UUID stageId, List<UUID> ids,
                                                   HttpServletRequest httpRequest) {
        PipelineCatalog catalog = catalogs.catalogOf(workspaceId);
        CandidateStage stage = stages.requireInWorkspace(stageId, workspaceId);
        List<CandidateStatusDefinition> live = catalog.liveStatusesOf(stage);
        if (!PipelineRules.sameMembers(ids, live.stream().map(CandidateStatusDefinition::getId).toList())) {
            throw ApiException.of(ErrorCode.PIPELINE_ORDER_INVALID);
        }
        int position = 1;
        for (UUID id : ids) {
            CandidateStatusDefinition status = live.stream().filter(s -> s.getId().equals(id)).findFirst().orElseThrow();
            status.placeAt(position++);
        }
        for (CandidateStatusDefinition retired : catalog.statusesOf(stage).stream()
                .filter(CandidateStatusDefinition::isRetired).toList()) {
            retired.placeAt(position++);
        }
        audit.record(userId, workspaceId, "statusesReordered", stageId, httpRequest);
        return PipelineCatalogMapper.toResponse(catalogs.catalogOf(workspaceId));
    }

    private void rename(UUID workspaceId, CandidateStatusDefinition status, String requested) {
        String label = requested.strip();
        if (label.isEmpty()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "label", "A status needs a name");
        }
        statuses.findByLabel(workspaceId, label)
                .filter(existing -> !existing.getId().equals(status.getId()))
                .ifPresent(existing -> {
                    throw ApiException.of(ErrorCode.PIPELINE_LABEL_TAKEN);
                });
        status.rename(label);
    }

    /** Taking a status out moves everyone at it to Inactive at once; its stage's default is never taken out. */
    private void flag(PipelineCatalog catalog, CandidateStatusDefinition status, boolean out) {
        if (out && status.isDefaultOfStage()) {
            throw ApiException.of(ErrorCode.PIPELINE_DEFAULT_IN_PROCESS);
        }
        status.markOutOfProcess(out);
        if (!out) {
            defaults.claimIfFree(catalog, status);
        }
    }

    private void move(PipelineCatalog catalog, CandidateStatusDefinition status, CandidateStage target) {
        PipelineRules.refuseRetiredStage(target);
        CandidateStage from = status.getStage();
        boolean wasDefault = status.isDefaultOfStage();
        status.moveTo(target, PipelineRules.nextPositionIn(catalog, target));
        statuses.flush();
        if (wasDefault) {
            defaults.handOn(catalog, from, status);
        }
        defaults.claimIfFree(catalog, status);
    }

    private void makeDefault(PipelineCatalog catalog, CandidateStatusDefinition status) {
        if (status.isRetired()) {
            throw ApiException.of(ErrorCode.PIPELINE_STATUS_RETIRED);
        }
        if (status.isOutOfProcess()) {
            throw ApiException.of(ErrorCode.PIPELINE_DEFAULT_IN_PROCESS);
        }
        catalog.defaultOf(status.getStage()).ifPresent(defaults::demote);
        status.makeDefault();
    }
}
