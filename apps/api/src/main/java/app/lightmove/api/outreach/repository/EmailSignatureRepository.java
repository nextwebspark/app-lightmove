package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.model.EmailSignature;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder takes the workspace and the owner: a signature is read by nobody else. */
public interface EmailSignatureRepository extends JpaRepository<EmailSignature, UUID> {

    List<EmailSignature> findByWorkspaceIdAndUserIdOrderByCreatedAtAsc(UUID workspaceId, UUID userId);

    Optional<EmailSignature> findByIdAndWorkspaceIdAndUserId(UUID id, UUID workspaceId, UUID userId);

    Optional<EmailSignature> findByWorkspaceIdAndUserIdAndIsDefaultTrue(UUID workspaceId, UUID userId);

    long countByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    /**
     * Run before the new default is marked, never after: the partial unique index is checked row by row, so a flush that
     * marked the new one first would meet the old one still set.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE EmailSignature s SET s.isDefault = false "
            + "WHERE s.workspaceId = :workspaceId AND s.userId = :userId AND s.isDefault = true")
    int clearDefault(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);
}
