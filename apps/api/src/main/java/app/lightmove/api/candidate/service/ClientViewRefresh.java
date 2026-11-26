package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.core.stream.ProjectStreamKind;
import app.lightmove.api.core.stream.ProjectStreamPublisher;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells an open position page that someone may have left a client's view (#1488): a restriction set or lifted, or a
 * share answered. V246's triggers have already moved the row; this only makes the client's grid read it again.
 */
@Service
@RequiredArgsConstructor
public class ClientViewRefresh {

    private final CandidateRepository candidates;
    private final ProjectStreamPublisher stream;

    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshPositionsOf(UUID personId) {
        candidates.findProjectIdsByPersonId(personId).forEach(this::refreshPosition);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshPosition(UUID projectId) {
        stream.publish(projectId, ProjectStreamKind.CANDIDATE_EDITED);
    }
}
