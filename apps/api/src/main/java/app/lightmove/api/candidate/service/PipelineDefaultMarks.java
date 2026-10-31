package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.CandidateStage;
import app.lightmove.api.candidate.model.CandidateStatusDefinition;
import app.lightmove.api.candidate.model.PipelineCatalog;
import app.lightmove.api.candidate.repository.CandidateStatusDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Where a stage's default goes when its status leaves. A card dropped on a stage lands on its default, so the mark
 * only ever passes to a live status in process; a stage with none of those has no default.
 */
@Component
@RequiredArgsConstructor
class PipelineDefaultMarks {

    private final CandidateStatusDefinitionRepository statuses;

    /** The default is cleared and flushed on its own, so the one-default-per-stage index never sees two. */
    void demote(CandidateStatusDefinition status) {
        status.clearDefault();
        statuses.flush();
    }

    void retire(PipelineCatalog catalog, CandidateStatusDefinition status) {
        if (status.isDefaultOfStage()) {
            demote(status);
            handOn(catalog, status.getStage(), status);
        }
        status.retire();
    }

    void handOn(PipelineCatalog catalog, CandidateStage stage, CandidateStatusDefinition leaving) {
        catalog.liveStatusesOf(stage).stream()
                .filter(candidate -> !candidate.getId().equals(leaving.getId()))
                .filter(CandidateStatusDefinition::isInProcess)
                .findFirst()
                .ifPresent(CandidateStatusDefinition::makeDefault);
    }

    /** The mark for a status arriving where nobody holds it, as long as a card could stand there. */
    void claimIfFree(PipelineCatalog catalog, CandidateStatusDefinition status) {
        if (status.isInProcess() && catalog.defaultOf(status.getStage()).isEmpty()) {
            status.makeDefault();
        }
    }
}
