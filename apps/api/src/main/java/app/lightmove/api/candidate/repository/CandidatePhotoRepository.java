package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.CandidatePhoto;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidatePhotoRepository extends JpaRepository<CandidatePhoto, UUID> {

    Optional<CandidatePhoto> findByCandidateId(UUID candidateId);

    boolean existsByCandidateId(UUID candidateId);
}
