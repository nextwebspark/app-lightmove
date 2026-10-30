package app.lightmove.api.core.security.mfa;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {

    /** One statement, so "usable once" holds without a read: two sign-ins racing on one code cannot both get 1. */
    @Modifying
    @Query("""
            UPDATE MfaRecoveryCode c
               SET c.usedAt = :now
             WHERE c.userId = :userId
               AND c.codeHash = :codeHash
               AND c.usedAt IS NULL
            """)
    int consume(@Param("userId") UUID userId, @Param("codeHash") String codeHash, @Param("now") Instant now);

    long countByUserIdAndUsedAtIsNull(UUID userId);

    @Modifying
    @Query("DELETE FROM MfaRecoveryCode c WHERE c.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
