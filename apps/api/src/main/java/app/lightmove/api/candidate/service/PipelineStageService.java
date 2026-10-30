package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PipelineColour;
import app.lightmove.api.candidate.dto.CreatePipelineStageRequest;
import app.lightmove.api.candidate.dto.PipelineCatalogResponse;
import app.lightmove.api.candidate.dto.UpdatePipelineStageRequest;
import app.lightmove.api.candidate.model.CandidateStage;
import app.lightmove.api.candidate.model.PipelineCatalog;
import app.lightmove.api.candidate.repository.CandidateStageRepository;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An admin's edits to the pipeline's stages. Every stage is alike: a new one goes after the live ones, any may be
 * reordered, and one retires only once none of its statuses is live, or with them, while another live stage stays.
 * Every write answers the whole catalog and is one audit event.
 */
@Service
@RequiredArgsConstructor
public class PipelineStageService {

    private final CandidatePipelineCatalog catalogs;
    private final CandidateStageRepository stages;
    private final PipelineDefaultMarks defaults;
    private final PipelineEditAudit audit;

    @Transactional
    public PipelineCatalogResponse createStage(UUID userId, UUID workspaceId, CreatePipelineStageRequest request,
                                               HttpServletRequest httpRequest) {
        PipelineCatalog catalog = catalogs.catalogOf(workspaceId);
        String label = request.label().strip();
        refuseLabelTaken(workspaceId, label, null);
        String key = PipelineRules.freeKey(label, "stage",
                candidate -> stages.existsByWorkspaceIdAndStageKey(workspaceId, candidate));
        CandidateStage stage = stages.save(CandidateStage.added(workspaceId, key, label,
                PipelineRules.colourOf(request.colour(), PipelineColour.NEUTRAL), 0, userId));
        List<CandidateStage> order = new ArrayList<>(catalog.liveStages());
        order.add(stage);
        catalog.stages().stream().filter(CandidateStage::isRetired).forEach(order::add);
        renumber(order);
        audit.record(userId, workspaceId, "stageCreated", stage.getId(), httpRequest);
        return PipelineCatalogMapper.toResponse(catalogs.catalogOf(workspaceId));
    }

    @Transactional
    public PipelineCatalogResponse updateStage(UUID userId, UUID workspaceId, UUID stageId,
                                               UpdatePipelineStageRequest request, HttpServletRequest httpRequest) {
        PipelineCatalog catalog = catalogs.catalogOf(workspaceId);
        CandidateStage stage = stages.requireInWorkspace(stageId, workspaceId);
        if (request.label() != null) {
            String label = request.label().strip();
            if (label.isEmpty()) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "label", "A stage needs a name");
            }
            refuseLabelTaken(workspaceId, label, stageId);
            stage.rename(label);
        }
        if (request.colour() != null) {
            stage.recolour(PipelineRules.requireColour(request.colour()));
        }
        if (Boolean.TRUE.equals(request.retired()) && !stage.isRetired()) {
            refuseRetiring(catalog, stage, Boolean.TRUE.equals(request.retireStatuses()));
            PipelineRules.refuseLeavingNoStart(catalog, catalog.liveStatusesOf(stage));
            catalog.liveStatusesOf(stage).forEach(status -> defaults.retire(catalog, status));
            stage.retire();
        } else if (Boolean.FALSE.equals(request.retired())) {
            stage.restore();
        }
        audit.record(userId, workspaceId, "stageUpdated", stageId, httpRequest);
        return PipelineCatalogMapper.toResponse(catalogs.catalogOf(workspaceId));
    }

    /** {@code ids} names every live stage once; retired stages keep their place behind them. */
    @Transactional
    public PipelineCatalogResponse reorderStages(UUID userId, UUID workspaceId, List<UUID> ids,
                                                 HttpServletRequest httpRequest) {
        PipelineCatalog catalog = catalogs.catalogOf(workspaceId);
        List<CandidateStage> live = catalog.liveStages();
        if (!PipelineRules.sameMembers(ids, live.stream().map(CandidateStage::getId).toList())) {
            throw ApiException.of(ErrorCode.PIPELINE_ORDER_INVALID);
        }
        List<CandidateStage> order = new ArrayList<>();
        ids.forEach(id -> live.stream().filter(stage -> stage.getId().equals(id)).findFirst().ifPresent(order::add));
        catalog.stages().stream().filter(CandidateStage::isRetired).forEach(order::add);
        renumber(order);
        audit.record(userId, workspaceId, "stagesReordered", null, httpRequest);
        return PipelineCatalogMapper.toResponse(catalogs.catalogOf(workspaceId));
    }

    private static void refuseRetiring(PipelineCatalog catalog, CandidateStage stage, boolean withStatuses) {
        if (!withStatuses && !catalog.liveStatusesOf(stage).isEmpty()) {
            throw ApiException.of(ErrorCode.PIPELINE_STAGE_NOT_EMPTY);
        }
        if (catalog.liveStages().size() <= 1) {
            throw ApiException.of(ErrorCode.PIPELINE_STAGE_REQUIRED);
        }
    }

    private void refuseLabelTaken(UUID workspaceId, String label, UUID renamedId) {
        stages.findByLabel(workspaceId, label)
                .filter(existing -> !existing.getId().equals(renamedId))
                .ifPresent(existing -> {
                    throw ApiException.of(ErrorCode.PIPELINE_LABEL_TAKEN);
                });
    }

    private static void renumber(List<CandidateStage> order) {
        int position = 1;
        for (CandidateStage stage : order) {
            stage.placeAt(position++);
        }
    }
}
