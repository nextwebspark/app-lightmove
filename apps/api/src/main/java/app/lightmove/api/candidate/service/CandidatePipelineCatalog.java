package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.CandidateSystemStatus;
import app.lightmove.api.candidate.model.CandidateStatusDefinition;
import app.lightmove.api.candidate.model.PipelineCatalog;
import app.lightmove.api.candidate.model.PipelineProgressAnchors;
import app.lightmove.api.candidate.repository.CandidateStageRepository;
import app.lightmove.api.candidate.repository.CandidateStatusDefinitionRepository;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one door to a workspace's stages and statuses: every write that sets a status, every filter that
 * names one, and every rule that asks where someone stands reads the catalog through here, so a status
 * key is always resolved against the caller's own workspace.
 */
@Service
@RequiredArgsConstructor
public class CandidatePipelineCatalog {

    private final CandidateStageRepository stages;
    private final CandidateStatusDefinitionRepository statuses;
    private final CandidatePipelineSeeder seeder;

    @Transactional(readOnly = true)
    public PipelineCatalog catalogOf(UUID workspaceId) {
        if (!stages.existsByWorkspaceId(workspaceId)) {
            seeder.seedIfAbsent(workspaceId);
        }
        return new PipelineCatalog(stages.findByWorkspaceIdOrderByPositionAsc(workspaceId),
                statuses.findCatalogOf(workspaceId));
    }

    /**
     * The status a write names, which must be this workspace's and still offered; null or blank is the
     * catalog's default. An unknown key is a 400 rather than a 404: it is a bad value in a body, as an
     * unknown enum token always was.
     */
    @Transactional(readOnly = true)
    public CandidateStatusDefinition requireOfferable(UUID workspaceId, String statusKey) {
        PipelineCatalog catalog = catalogOf(workspaceId);
        if (statusKey == null || statusKey.isBlank()) {
            return catalog.defaultStatus();
        }
        CandidateStatusDefinition status = catalog.byKey(statusKey.strip())
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Unknown candidate status: " + statusKey));
        if (status.isRetired()) {
            throw ApiException.of(ErrorCode.PIPELINE_STATUS_RETIRED);
        }
        return status;
    }

    @Transactional(readOnly = true)
    public PipelineProgressAnchors progressAnchors(UUID workspaceId) {
        return catalogOf(workspaceId).progressAnchors();
    }

    @Transactional(readOnly = true)
    public CandidateStatusDefinition system(UUID workspaceId, CandidateSystemStatus system) {
        return catalogOf(workspaceId).bySystemKey(system);
    }
}
