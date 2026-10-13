package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.repository.CandidateStageRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a workspace founded after V131 with the starting catalog the first time anything reads it.
 * {@code REQUIRES_NEW}, and its own bean so the proxy applies: the first read is often inside a read-only
 * transaction, which Postgres refuses an insert in, and the read that follows sees the committed rows.
 */
@Component
@RequiredArgsConstructor
class CandidatePipelineSeeder {

    private final CandidateStageRepository stages;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedIfAbsent(UUID workspaceId) {
        stages.seedIfAbsent(workspaceId);
    }
}
