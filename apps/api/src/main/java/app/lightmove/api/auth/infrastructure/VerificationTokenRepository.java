package app.lightmove.api.auth.infrastructure;

import app.lightmove.api.auth.domain.TokenPurpose;
import app.lightmove.api.auth.domain.VerificationToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    /**
     * Burns any outstanding tokens for this user and purpose.
     *
     * <p>Called before issuing a new one, so that "resend the email" leaves exactly one working link
     * rather than a growing pile of them across the user's inbox.
     */
    @Modifying
    @Query("""
            UPDATE VerificationToken t
               SET t.consumedAt = :now
             WHERE t.userId = :userId
               AND t.purpose = :purpose
               AND t.consumedAt IS NULL
            """)
    int consumeOutstanding(@Param("userId") UUID userId,
                           @Param("purpose") TokenPurpose purpose,
                           @Param("now") Instant now);
}
