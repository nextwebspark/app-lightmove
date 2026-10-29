package app.lightmove.api.core.security.mfa;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MfaFactorRepository extends JpaRepository<MfaFactor, UUID> {

    Optional<MfaFactor> findByUserId(UUID userId);

    /** Under the row lock: two sign-ins presenting one code in one step must not both be let in. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM MfaFactor f WHERE f.userId = :userId")
    Optional<MfaFactor> findForUpdateByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(f) > 0 FROM MfaFactor f WHERE f.userId = :userId AND f.enabledAt IS NOT NULL")
    boolean existsEnabledForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM MfaFactor f WHERE f.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
