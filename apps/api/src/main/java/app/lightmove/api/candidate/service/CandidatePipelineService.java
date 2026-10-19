package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PipelineColour;
import app.lightmove.api.candidate.constant.PipelineStageKind;
import app.lightmove.api.candidate.dto.CreatePipelineStageRequest;
import app.lightmove.api.candidate.dto.CreatePipelineStatusRequest;
import app.lightmove.api.candidate.dto.PipelineCatalogResponse;
import app.lightmove.api.candidate.dto.PipelineRetirePreviewResponse;
import app.lightmove.api.candidate.dto.PipelineStageResponse;
import app.lightmove.api.candidate.dto.PipelineStatusResponse;
import app.lightmove.api.candidate.dto.RetiredStatusPreview;
import app.lightmove.api.candidate.dto.UpdatePipelineStageRequest;
import app.lightmove.api.candidate.dto.UpdatePipelineStatusRequest;
import app.lightmove.api.candidate.model.CandidateStage;
import app.lightmove.api.candidate.model.CandidateStatusDefinition;
import app.lightmove.api.candidate.model.CandidateStatusCount;
import app.lightmove.api.candidate.model.PipelineCatalog;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.CandidateStageRepository;
import app.lightmove.api.candidate.repository.CandidateStatusDefinitionRepository;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An admin's edits to the workspace's pipeline, from Settings → Pipeline stages. Every write answers the
 * whole catalog and is one audit event (a {@code pipeline} section of the workspace).
 *
 * <p>The rules that keep the hierarchy whole: a status always has a stage; a stage with any live status has
 * exactly one default among them; a stage retires only once none of its statuses is live, or with them; and
 * there is exactly one closed stage, always last and never retired, beside at least one live active stage.
 * The seeded statuses are a starting point, retired and moved like any other.
 */
@Service
@RequiredArgsConstructor
public class CandidatePipelineService {

    private static final int MAX_KEY = 40;

    private final CandidatePipelineCatalog catalogs;
    private final CandidateStageRepository stages;
    private final CandidateStatusDefinitionRepository statuses;
    private final CandidateRepository candidates;
    private final StatusAutomationService automations;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PipelineCatalogResponse catalog(UUID workspaceId) {
        return toResponse(catalogs.catalogOf(workspaceId));
    }

    /** What retiring this stage with its statuses would touch, for the confirmation an admin reads first. */
    @Transactional(readOnly = true)
    public PipelineRetirePreviewResponse stageRetirePreview(UUID workspaceId, UUID stageId) {
        PipelineCatalog catalog = catalogs.catalogOf(workspaceId);
        CandidateStage stage = stages.requireInWorkspace(stageId, workspaceId);
        return previewOf(workspaceId, catalog.liveStatusesOf(stage));
    }

    @Transactional(readOnly = true)
    public PipelineRetirePreviewResponse statusRetirePreview(UUID workspaceId, UUID statusId) {
        CandidateStatusDefinition status = statuses.requireInWorkspace(statusId, workspaceId);
        return previewOf(workspaceId, status.isRetired() ? List.of() : List.of(status));
    }

    @Transactional
    public PipelineCatalogResponse createStage(UUID userId, UUID workspaceId, CreatePipelineStageRequest request,
                                               HttpServletRequest httpRequest) {
        PipelineCatalog catalog = catalogs.catalogOf(workspaceId);
        String label = request.label().strip();
        refuseStageLabelTaken(workspaceId, label, null);
        String key = freeKey(label, "stage", candidate -> stages.existsByWorkspaceIdAndStageKey(workspaceId, candidate));
        CandidateStage stage = stages.save(CandidateStage.added(workspaceId, key, label,
                colourOf(request.colour(), PipelineColour.NEUTRAL), 0, userId));
        List<CandidateStage> order = new ArrayList<>(catalog.stages().stream().filter(s -> !s.isClosed()).toList());
        order.add(stage);
        catalog.stages().stream().filter(CandidateStage::isClosed).forEach(order::add);
        renumberStages(order);
        record(userId, workspaceId, "stageCreated", stage.getId(), httpRequest);
        return toResponse(catalogs.catalogOf(workspaceId));
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
            refuseStageLabelTaken(workspaceId, label, stageId);
            stage.rename(label);
        }
        if (request.colour() != null) {
            stage.recolour(ApiValueEnum.require(PipelineColour.class, request.colour(), "pipeline colour"));
        }
        if (Boolean.TRUE.equals(request.retired()) && !stage.isRetired()) {
            refuseRetiring(catalog, stage, Boolean.TRUE.equals(request.retireStatuses()));
            refuseLeavingNoStart(catalog, catalog.liveStatusesOf(stage));
            catalog.liveStatusesOf(stage).forEach(status -> retireStatus(catalog, status));
            stage.retire();
        } else if (Boolean.FALSE.equals(request.retired())) {
            stage.restore();
        }
        record(userId, workspaceId, "stageUpdated", stageId, httpRequest);
        return toResponse(catalogs.catalogOf(workspaceId));
    }

    /** {@code ids} names every live stage once, the closed one last; retired stages keep their place behind them. */
    @Transactional
    public PipelineCatalogResponse reorderStages(UUID userId, UUID workspaceId, List<UUID> ids,
                                                 HttpServletRequest httpRequest) {
        PipelineCatalog catalog = catalogs.catalogOf(workspaceId);
        List<CandidateStage> live = catalog.liveStages();
        if (!sameMembers(ids, live.stream().map(CandidateStage::getId).toList())
                || !live.stream().filter(CandidateStage::isClosed).allMatch(closed -> closed.getId().equals(ids.getLast()))) {
            throw ApiException.of(ErrorCode.PIPELINE_ORDER_INVALID);
        }
        List<CandidateStage> order = new ArrayList<>();
        ids.stream().map(id -> live.stream().filter(stage -> stage.getId().equals(id)).findFirst().orElseThrow())
                .filter(stage -> !stage.isClosed())
                .forEach(order::add);
        catalog.stages().stream().filter(CandidateStage::isRetired).forEach(order::add);
        live.stream().filter(CandidateStage::isClosed).forEach(order::add);
        renumberStages(order);
        record(userId, workspaceId, "stagesReordered", null, httpRequest);
        return toResponse(catalogs.catalogOf(workspaceId));
    }

    @Transactional
    public PipelineCatalogResponse createStatus(UUID userId, UUID workspaceId, CreatePipelineStatusRequest request,
                                                HttpServletRequest httpRequest) {
        PipelineCatalog catalog = catalogs.catalogOf(workspaceId);
        CandidateStage stage = stages.requireInWorkspace(request.stageId(), workspaceId);
        refuseRetiredStage(stage);
        String label = request.label().strip();
        statuses.findByLabel(workspaceId, label).ifPresent(existing -> {
            throw ApiException.of(existing.isRetired() ? ErrorCode.PIPELINE_STATUS_RETIRED : ErrorCode.PIPELINE_LABEL_TAKEN);
        });
        String key = freeKey(label, "status", candidate -> statuses.existsByWorkspaceIdAndStatusKey(workspaceId, candidate));
        boolean firstLive = catalog.defaultOf(stage).isEmpty();
        CandidateStatusDefinition status = statuses.save(CandidateStatusDefinition.added(stage, key, label,
                colourOf(request.colour(), stage.getColour()), nextPositionIn(catalog, stage), firstLive, userId));
        record(userId, workspaceId, "statusCreated", status.getId(), httpRequest);
        return toResponse(catalogs.catalogOf(workspaceId));
    }

    @Transactional
    public PipelineCatalogResponse updateStatus(UUID userId, UUID workspaceId, UUID statusId,
                                                UpdatePipelineStatusRequest request, HttpServletRequest httpRequest) {
        PipelineCatalog catalog = catalogs.catalogOf(workspaceId);
        CandidateStatusDefinition status = statuses.requireInWorkspace(statusId, workspaceId);
        if (request.label() != null) {
            String label = request.label().strip();
            if (label.isEmpty()) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "label", "A status needs a name");
            }
            statuses.findByLabel(workspaceId, label)
                    .filter(existing -> !existing.getId().equals(statusId))
                    .ifPresent(existing -> {
                        throw ApiException.of(ErrorCode.PIPELINE_LABEL_TAKEN);
                    });
            status.rename(label);
        }
        if (request.colour() != null) {
            status.recolour(ApiValueEnum.require(PipelineColour.class, request.colour(), "pipeline colour"));
        }
        if (request.stageId() != null && !request.stageId().equals(status.getStage().getId())) {
            moveStatus(catalog, status, stages.requireInWorkspace(request.stageId(), workspaceId));
        }
        if (Boolean.TRUE.equals(request.isDefault()) && !status.isDefaultOfStage()) {
            if (status.isRetired()) {
                throw ApiException.of(ErrorCode.PIPELINE_STATUS_RETIRED);
            }
            catalog.defaultOf(status.getStage()).ifPresent(this::demote);
            status.makeDefault();
        }
        if (Boolean.TRUE.equals(request.retired()) && !status.isRetired()) {
            refuseLeavingNoStart(catalog, List.of(status));
            retireStatus(catalog, status);
        } else if (Boolean.FALSE.equals(request.retired()) && status.isRetired()) {
            refuseRetiredStage(status.getStage());
            status.restore();
            if (catalog.defaultOf(status.getStage()).isEmpty()) {
                status.makeDefault();
            }
        }
        record(userId, workspaceId, "statusUpdated", statusId, httpRequest);
        return toResponse(catalogs.catalogOf(workspaceId));
    }

    /** {@code ids} names every live status of the stage once; retired ones keep their place behind them. */
    @Transactional
    public PipelineCatalogResponse reorderStatuses(UUID userId, UUID workspaceId, UUID stageId, List<UUID> ids,
                                                   HttpServletRequest httpRequest) {
        PipelineCatalog catalog = catalogs.catalogOf(workspaceId);
        CandidateStage stage = stages.requireInWorkspace(stageId, workspaceId);
        List<CandidateStatusDefinition> live = catalog.liveStatusesOf(stage);
        if (!sameMembers(ids, live.stream().map(CandidateStatusDefinition::getId).toList())) {
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
        record(userId, workspaceId, "statusesReordered", stageId, httpRequest);
        return toResponse(catalogs.catalogOf(workspaceId));
    }

    private void moveStatus(PipelineCatalog catalog, CandidateStatusDefinition status, CandidateStage target) {
        refuseRetiredStage(target);
        if (target.isClosed()) {
            refuseLeavingNoStart(catalog, List.of(status));
        }
        CandidateStage from = status.getStage();
        boolean wasDefault = status.isDefaultOfStage();
        boolean targetHasDefault = catalog.defaultOf(target).isPresent();
        status.moveTo(target, nextPositionIn(catalog, target));
        statuses.flush();
        if (wasDefault) {
            promoteNextLive(catalog, from, status);
        }
        if (!targetHasDefault && !status.isRetired()) {
            status.makeDefault();
        }
    }

    private PipelineRetirePreviewResponse previewOf(UUID workspaceId, List<CandidateStatusDefinition> retiring) {
        List<UUID> ids = retiring.stream().map(CandidateStatusDefinition::getId).toList();
        Map<UUID, Long> holders = ids.isEmpty() ? Map.of() : candidates.countByStatusIdIn(ids).stream()
                .collect(Collectors.toMap(CandidateStatusCount::getStatusId, CandidateStatusCount::getTotal));
        return new PipelineRetirePreviewResponse(
                retiring.stream()
                        .map(status -> new RetiredStatusPreview(status.getId(), status.getLabel(),
                                holders.getOrDefault(status.getId(), 0L)))
                        .toList(),
                ids.isEmpty() ? List.of() : automations.pointingAt(workspaceId, ids));
    }

    /**
     * Someone filed with no status starts at a live status of a live active stage, so the last of those never
     * leaves: without one, every door would file new people onto a retired status.
     */
    private static void refuseLeavingNoStart(PipelineCatalog catalog, List<CandidateStatusDefinition> leaving) {
        boolean anotherRemains = catalog.liveStages().stream()
                .filter(stage -> stage.getKind() == PipelineStageKind.ACTIVE)
                .flatMap(stage -> catalog.liveStatusesOf(stage).stream())
                .anyMatch(status -> leaving.stream().noneMatch(gone -> gone.getId().equals(status.getId())));
        if (!anotherRemains) {
            throw ApiException.of(ErrorCode.PIPELINE_STATUS_REQUIRED);
        }
    }

    private void retireStatus(PipelineCatalog catalog, CandidateStatusDefinition status) {
        if (status.isDefaultOfStage()) {
            demote(status);
            promoteNextLive(catalog, status.getStage(), status);
        }
        status.retire();
    }

    private void refuseRetiring(PipelineCatalog catalog, CandidateStage stage, boolean withStatuses) {
        if (stage.isClosed()) {
            throw ApiException.of(ErrorCode.PIPELINE_STAGE_REQUIRED);
        }
        if (!withStatuses && !catalog.liveStatusesOf(stage).isEmpty()) {
            throw ApiException.of(ErrorCode.PIPELINE_STAGE_NOT_EMPTY);
        }
        long liveActive = catalog.liveStages().stream()
                .filter(live -> live.getKind() == PipelineStageKind.ACTIVE)
                .count();
        if (liveActive <= 1) {
            throw ApiException.of(ErrorCode.PIPELINE_STAGE_REQUIRED);
        }
    }

    /** The default is cleared and flushed on its own, so the one-default-per-stage index never sees two. */
    private void demote(CandidateStatusDefinition status) {
        status.clearDefault();
        statuses.flush();
    }

    private static void promoteNextLive(PipelineCatalog catalog, CandidateStage stage, CandidateStatusDefinition leaving) {
        catalog.liveStatusesOf(stage).stream()
                .filter(candidate -> !candidate.getId().equals(leaving.getId()))
                .findFirst()
                .ifPresent(CandidateStatusDefinition::makeDefault);
    }

    private void refuseStageLabelTaken(UUID workspaceId, String label, UUID renamedId) {
        stages.findByLabel(workspaceId, label)
                .filter(existing -> !existing.getId().equals(renamedId))
                .ifPresent(existing -> {
                    throw ApiException.of(ErrorCode.PIPELINE_LABEL_TAKEN);
                });
    }

    private static void refuseRetiredStage(CandidateStage stage) {
        if (stage.isRetired()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "That stage is retired");
        }
    }

    private static int nextPositionIn(PipelineCatalog catalog, CandidateStage stage) {
        return catalog.statusesOf(stage).stream().mapToInt(CandidateStatusDefinition::getPosition).max().orElse(0) + 1;
    }

    private static void renumberStages(List<CandidateStage> order) {
        int position = 1;
        for (CandidateStage stage : order) {
            stage.placeAt(position++);
        }
    }

    private static boolean sameMembers(List<UUID> asked, List<UUID> held) {
        return asked.size() == held.size() && new HashSet<>(asked).equals(new HashSet<>(held));
    }

    private static PipelineColour colourOf(String token, PipelineColour fallback) {
        return ApiValueEnum.parse(PipelineColour.class, token, fallback, "pipeline colour");
    }

    /**
     * A wire key from a label — "Waiting on visa" is {@code waitingOnVisa} — numbered past any key the
     * workspace already holds, retired ones included. Written once: a rename never changes it.
     */
    static String freeKey(String label, String fallback, Predicate<String> taken) {
        StringBuilder key = new StringBuilder();
        for (String word : label.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (word.isEmpty()) {
                continue;
            }
            key.append(key.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        String base = key.isEmpty() || !Character.isLetter(key.charAt(0)) ? fallback + key : key.toString();
        base = base.length() > MAX_KEY - 4 ? base.substring(0, MAX_KEY - 4) : base;
        String candidate = base;
        for (int suffix = 2; taken.test(candidate); suffix++) {
            candidate = base + suffix;
        }
        return candidate;
    }

    private void record(UUID userId, UUID workspaceId, String change, UUID targetId, HttpServletRequest httpRequest) {
        audit.event(WorkspaceEventType.WORKSPACE_UPDATED).actor(userId).workspace(workspaceId).from(httpRequest)
                .detail("section", "pipeline")
                .detail("change", change)
                .detailIfPresent("targetId", targetId == null ? null : targetId.toString())
                .record();
    }

    static PipelineCatalogResponse toResponse(PipelineCatalog catalog) {
        return new PipelineCatalogResponse(catalog.stages().stream()
                .map(stage -> new PipelineStageResponse(stage.getId(), stage.getStageKey(), stage.getLabel(),
                        stage.getColour().value(), stage.getKind().value(), stage.getPosition(), stage.isSystem(),
                        stage.isRetired(),
                        catalog.statusesOf(stage).stream()
                                .map(status -> new PipelineStatusResponse(status.getId(), status.getStatusKey(),
                                        status.getLabel(), status.getColour().value(), status.getPosition(),
                                        status.isDefaultOfStage(), status.isSystem(), status.isRetired()))
                                .toList()))
                .toList());
    }
}
