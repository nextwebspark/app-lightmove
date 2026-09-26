package app.lightmove.api.core.security.token;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Rotation must use nothing else: without {@code FOR UPDATE} two concurrent refreshes both read the
     * token un-revoked and <b>reuse detection never fires</b> — the expected shape of the attack.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId, Instant now);

    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now,
                   t.revokedReason = :reason
             WHERE t.familyId = :familyId
               AND t.revokedAt IS NULL
            """)
    int revokeFamily(@Param("familyId") UUID familyId,
                     @Param("reason") RevokeReason reason,
                     @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now,
                   t.revokedReason = :reason
             WHERE t.userId = :userId
               AND t.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") UUID userId,
                         @Param("reason") RevokeReason reason,
                         @Param("now") Instant now);

    /** Ownership is in the WHERE clause: 0 means both "no such session" and "not yours", deliberately alike. */
    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now,
                   t.revokedReason = :reason
             WHERE t.familyId = :familyId
               AND t.userId = :userId
               AND t.revokedAt IS NULL
            """)
    int revokeFamilyForUser(@Param("familyId") UUID familyId,
                            @Param("userId") UUID userId,
                            @Param("reason") RevokeReason reason,
                            @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now,
                   t.revokedReason = :reason
             WHERE t.userId = :userId
               AND t.client = :client
               AND t.revokedAt IS NULL
            """)
    int revokeAllForUserAndClient(@Param("userId") UUID userId,
                                  @Param("client") SessionClient client,
                                  @Param("reason") RevokeReason reason,
                                  @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now,
                   t.revokedReason = :reason
             WHERE t.userId = :userId
               AND t.familyId <> :keptFamilyId
               AND t.revokedAt IS NULL
            """)
    int revokeAllForUserExceptFamily(@Param("userId") UUID userId,
                                     @Param("keptFamilyId") UUID keptFamilyId,
                                     @Param("reason") RevokeReason reason,
                                     @Param("now") Instant now);
}
