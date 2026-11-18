package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.CandidateStatusDefinition;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A status anyone still holds is not retired, alone or with its stage: the people on it are moved first. Counted as
 * the retire preview counts them, across the workspace's positions.
 */
@Component
@RequiredArgsConstructor
class HeldStatusGuard {

    private final CandidateRepository candidates;

    void refuseRetiringHeld(List<CandidateStatusDefinition> retiring) {
        if (retiring.isEmpty()) {
            return;
        }
        boolean held = candidates.countByStatusIdIn(retiring.stream().map(CandidateStatusDefinition::getId).toList())
                .stream()
                .anyMatch(count -> count.getTotal() > 0);
        if (held) {
            throw ApiException.of(ErrorCode.PIPELINE_STATUS_HELD);
        }
    }
}
