package app.lightmove.api.core.security.mfa;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM MfaChallenge c WHERE c.tokenHash = :tokenHash")
    Optional<MfaChallenge> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);

    /** Before a new one: a second sign-in leaves one live challenge, and the table never grows past one per person. */
    @Modifying
    @Query("DELETE FROM MfaChallenge c WHERE c.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
