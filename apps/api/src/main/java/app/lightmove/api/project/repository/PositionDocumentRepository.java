package app.lightmove.api.project.repository;

import app.lightmove.api.project.model.PositionDocument;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reached only through its position, which the service has already scoped to the caller's workspace. */
public interface PositionDocumentRepository extends JpaRepository<PositionDocument, UUID> {

    Optional<PositionDocument> findByPositionId(UUID positionId);

    void deleteByPositionId(UUID positionId);
}
