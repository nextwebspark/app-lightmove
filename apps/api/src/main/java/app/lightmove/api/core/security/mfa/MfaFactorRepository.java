package app.lightmove.api.core.security.mfa;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
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

    List<MfaFactor> findByUserIdInAndEnabledAtIsNotNull(Collection<UUID> userIds);

    /** Native: the policy lives on the workspace row, which core reads without depending on its entity. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM app_lm_workspace w WHERE w.id = :workspaceId AND w.require_mfa) "
            + "AND NOT EXISTS (SELECT 1 FROM app_lm_user_mfa_factor f "
            + "WHERE f.user_id = :userId AND f.enabled_at IS NOT NULL)", nativeQuery = true)
    boolean workspaceRequiresAndUserLacks(@Param("userId") UUID userId, @Param("workspaceId") UUID workspaceId);

    @Query(value = "SELECT w.name FROM app_lm_workspace w JOIN app_lm_workspace_member m ON m.workspace_id = w.id "
            + "WHERE m.user_id = :userId AND m.status = 'ACTIVE' AND w.status = 'ACTIVE' AND w.require_mfa "
            + "ORDER BY w.name", nativeQuery = true)
    List<String> namesOfWorkspacesRequiring(@Param("userId") UUID userId);

    /** A pending row where there is none, so two first setups wait on one row rather than one failing on the key. */
    @Modifying
    @Query(value = "INSERT INTO app_lm_user_mfa_factor (user_id, secret_encrypted) VALUES (:userId, :secretEncrypted) "
            + "ON CONFLICT (user_id) DO NOTHING", nativeQuery = true)
    void insertPendingIfMissing(@Param("userId") UUID userId, @Param("secretEncrypted") String secretEncrypted);
}
