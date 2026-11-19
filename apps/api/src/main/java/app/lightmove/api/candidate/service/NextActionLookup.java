package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.dto.CandidateNextActionDto;
import app.lightmove.api.candidate.dto.CandidateOpenTaskDto;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * Each executive's next action on a position, answered by {@code task}: the Pipeline page reads it here, and
 * {@code task}, which already depends on {@code candidate}, implements it so the dependency stays one-way.
 */
@NamedInterface(name = "api", propagate = false)
public interface NextActionLookup {

    /** Keyed by candidate id; an executive with no open task is absent. */
    Map<UUID, CandidateNextActionDto> nextActionsOf(UUID projectId, Collection<UUID> candidateIds);

    /** Every open task on each executive, soonest due first; keyed by candidate id, an executive with none absent. */
    Map<UUID, List<CandidateOpenTaskDto>> openTasksOf(UUID projectId, Collection<UUID> candidateIds);
}
