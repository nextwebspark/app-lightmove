package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.dto.PipelineCatalogResponse;
import app.lightmove.api.candidate.dto.PipelineRetirePreviewResponse;
import app.lightmove.api.candidate.dto.RetiredStatusPreview;
import app.lightmove.api.candidate.model.CandidateStage;
import app.lightmove.api.candidate.model.CandidateStatusCount;
import app.lightmove.api.candidate.model.CandidateStatusDefinition;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.CandidateStageRepository;
import app.lightmove.api.candidate.repository.CandidateStatusDefinitionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → Pipeline stages' reads: the whole catalog, and what a change would touch — who holds each status and
 * the automations pointing at it — for the confirmation an admin reads before retiring a stage or a status, or
 * taking one out of process.
 */
@Service
@RequiredArgsConstructor
public class PipelineCatalogService {

    private final CandidatePipelineCatalog catalogs;
    private final CandidateStageRepository stages;
    private final CandidateStatusDefinitionRepository statuses;
    private final CandidateRepository candidates;
    private final StatusAutomationService automations;

    @Transactional(readOnly = true)
    public PipelineCatalogResponse catalog(UUID workspaceId) {
        return PipelineCatalogMapper.toResponse(catalogs.catalogOf(workspaceId));
    }

    @Transactional(readOnly = true)
    public PipelineRetirePreviewResponse stageRetirePreview(UUID workspaceId, UUID stageId) {
        CandidateStage stage = stages.requireInWorkspace(stageId, workspaceId);
        return previewOf(workspaceId, catalogs.catalogOf(workspaceId).liveStatusesOf(stage));
    }

    @Transactional(readOnly = true)
    public PipelineRetirePreviewResponse statusRetirePreview(UUID workspaceId, UUID statusId) {
        CandidateStatusDefinition status = statuses.requireInWorkspace(statusId, workspaceId);
        return previewOf(workspaceId, status.isRetired() ? List.of() : List.of(status));
    }

    private PipelineRetirePreviewResponse previewOf(UUID workspaceId, List<CandidateStatusDefinition> touched) {
        List<UUID> ids = touched.stream().map(CandidateStatusDefinition::getId).toList();
        Map<UUID, Long> holders = ids.isEmpty() ? Map.of() : candidates.countByStatusIdIn(ids).stream()
                .collect(Collectors.toMap(CandidateStatusCount::getStatusId, CandidateStatusCount::getTotal));
        return new PipelineRetirePreviewResponse(
                touched.stream()
                        .map(status -> new RetiredStatusPreview(status.getId(), status.getLabel(),
                                holders.getOrDefault(status.getId(), 0L)))
                        .toList(),
                ids.isEmpty() ? List.of() : automations.pointingAt(workspaceId, ids));
    }
}
