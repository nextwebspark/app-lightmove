package app.lightmove.api.auth.infrastructure;

import app.lightmove.api.auth.domain.RefreshToken;
import app.lightmove.api.auth.domain.RevokeReason;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Live sessions, for Settings → Active sessions. */
    List<RefreshToken> findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId, Instant now);

    /**
     * Kills an entire login session at once.
     *
     * <p>The blunt instrument behind reuse detection: when a rotated-away token reappears we cannot
     * distinguish the victim from the thief, so every token in the family goes, and both are made to
     * sign in again.
     */
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

    /** Signs the user out everywhere — used on password change. */
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
}
