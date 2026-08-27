package app.lightmove.api.position.repository;

import app.lightmove.api.position.model.PositionDocument;
import app.lightmove.api.position.model.PositionDocumentSummary;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Documents are only ever reached through their position, which the service has already scoped to the
 * caller's workspace — the same reason {@link PositionRepository} carries no workspace finder.
 */
public interface PositionDocumentRepository extends JpaRepository<PositionDocument, UUID> {

    /** Metadata only. Every read of the brief goes through here — see {@link PositionDocumentSummary}. */
    Optional<PositionDocumentSummary> findSummaryByPositionId(UUID positionId);

    /** The whole row, bytes included. Only the download, the replace and the delete need this. */
    Optional<PositionDocument> findByPositionId(UUID positionId);
}
