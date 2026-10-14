package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.CandidateScorecard;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateScorecardRepository extends JpaRepository<CandidateScorecard, UUID> {

    Optional<CandidateScorecard> findByCandidateIdAndWorkspaceId(UUID candidateId, UUID workspaceId);
}
