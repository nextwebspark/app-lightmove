package app.lightmove.api.core.security.apikey;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder takes the workspace: a key is tenant data. */
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<ApiKey> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    List<ApiKey> findByWorkspaceIdAndOwnerUserIdOrderByCreatedAtDesc(UUID workspaceId, UUID ownerUserId);

    List<ApiKey> findByWorkspaceIdAndOwnerUserIdAndRevokedAtIsNull(UUID workspaceId, UUID ownerUserId);

    List<ApiKey> findByWorkspaceIdAndRevokedAtIsNull(UUID workspaceId);

    @Query("""
            SELECT count(k) FROM ApiKey k
            WHERE k.workspaceId = :workspaceId AND k.ownerUserId = :ownerUserId
              AND k.revokedAt IS NULL AND k.expiresAt > :now
            """)
    long countLivePersonal(@Param("workspaceId") UUID workspaceId, @Param("ownerUserId") UUID ownerUserId,
                           @Param("now") Instant now);
}
