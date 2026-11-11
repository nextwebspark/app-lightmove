package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.CandidateScorecard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateScorecardRepository extends JpaRepository<CandidateScorecard, UUID> {

    Optional<CandidateScorecard> findByCandidateIdAndWorkspaceId(UUID candidateId, UUID workspaceId);

    /** Every assessment of one position: the Ranking chapter's read. */
    List<CandidateScorecard> findByProjectIdAndWorkspaceId(UUID projectId, UUID workspaceId);
}
