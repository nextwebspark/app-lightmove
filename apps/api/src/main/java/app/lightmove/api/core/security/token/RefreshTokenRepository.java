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
     * The same lookup, holding a row lock until the transaction commits. Used by rotation, and rotation
     * must use nothing else.
     *
     * <p>Rotation is read-then-write: it checks whether the token is already revoked, and if not, burns
     * it and mints a successor. Without a lock those two steps interleave. Two concurrent refreshes of
     * one token both read it un-revoked, both decide it is fresh, and both mint successors in the same
     * family — so the token was redeemed twice and <b>reuse detection never fires</b>. That is precisely
     * the case it exists to catch: an attacker racing the victim with a stolen token is not a rare
     * scenario, it is the expected shape of the attack.
     *
     * <p>{@code PESSIMISTIC_WRITE} issues {@code SELECT … FOR UPDATE}: the second transaction blocks on
     * the row until the first commits, then reads it revoked and correctly treats it as reuse.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

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
