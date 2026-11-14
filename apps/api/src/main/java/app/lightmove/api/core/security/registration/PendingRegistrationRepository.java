package app.lightmove.api.core.security.registration;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, UUID> {

    Optional<PendingRegistration> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PendingRegistration p WHERE p.tokenHash = :tokenHash")
    Optional<PendingRegistration> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);

    /** Before a new hold: one live hold per provider identity, so two tabs' sign-ins cannot both register. */
    @Modifying
    @Query("DELETE FROM PendingRegistration p WHERE p.provider = :provider AND p.subject = :subject")
    int deleteByProviderAndSubject(@Param("provider") String provider, @Param("subject") String subject);

    @Modifying
    @Query("DELETE FROM PendingRegistration p WHERE p.expiresAt < :now")
    int deleteExpiredBefore(@Param("now") Instant now);
}
