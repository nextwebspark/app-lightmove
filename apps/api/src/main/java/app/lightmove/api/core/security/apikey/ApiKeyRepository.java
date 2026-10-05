package app.lightmove.api.core.security.apikey;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Every finder takes the workspace, a key being tenant data, but the hash lookup that tells which workspace a key is. */
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

    Optional<ApiKey> findByTokenHash(String tokenHash);

    /** A bulk write, so authenticating a request bumps no entity version; the guard keeps two racing stamps to one. */
    @Transactional
    @Modifying
    @Query("""
            UPDATE ApiKey k SET k.lastUsedAt = :now, k.lastUsedIp = :ip
            WHERE k.id = :id AND (k.lastUsedAt IS NULL OR k.lastUsedAt < :staleBefore)
            """)
    int stampUse(@Param("id") UUID id, @Param("now") Instant now, @Param("ip") String ip,
                 @Param("staleBefore") Instant staleBefore);
}
