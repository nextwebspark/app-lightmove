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
     * <p>Rotation is read-then-write, and without a lock the two steps interleave: two concurrent
     * refreshes of one token both read it un-revoked and both mint successors, so it is redeemed twice
     * and <b>reuse detection never fires</b> — which is the expected shape of the attack, not a rare
     * case. {@code PESSIMISTIC_WRITE} issues {@code SELECT … FOR UPDATE}, so the second transaction
     * blocks, then reads it revoked and treats it as reuse.
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

    /**
     * Ends one session from Settings → Active sessions.
     *
     * <p>Ownership is in the WHERE clause rather than in a prior read: another user's family updates
     * nothing, so a 0 return is both "no such session" and "not yours", which is the answer we want to
     * give for either.
     */
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

    /** Ends the account's sessions on one client — pairing the extension replaces the one it held. */
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

    /** "Sign out all others": everything but the family the caller is refreshing on. */
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
