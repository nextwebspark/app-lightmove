package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.WorkspaceMailIntegration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** A workspace's OAuth app choices. Every finder takes the workspace id: the rows are tenant data. */
public interface WorkspaceMailIntegrationRepository extends JpaRepository<WorkspaceMailIntegration, UUID> {

    List<WorkspaceMailIntegration> findByWorkspaceId(UUID workspaceId);

    Optional<WorkspaceMailIntegration> findByWorkspaceIdAndProvider(UUID workspaceId, IntegrationProvider provider);

    /** Own apps whose secret expires by {@code by}: the expiry warning's daily read, across workspaces by design. */
    List<WorkspaceMailIntegration> findByModeAndSecretExpiresOnLessThanEqual(CredentialMode mode, LocalDate by);

    /**
     * Claims one expiry warning, committed before any email goes: of two instances running the daily check, only
     * the one whose update lands sends it. Bound to the expiry date read, so a date changed since claims nothing.
     */
    @Modifying
    @Transactional
    @Query("""
            update WorkspaceMailIntegration i set i.secretExpiryWarnedDays = :threshold
            where i.id = :id and i.secretExpiresOn = :expiresOn
              and (i.secretExpiryWarnedDays is null or i.secretExpiryWarnedDays > :threshold)
            """)
    int claimExpiryWarning(@Param("id") UUID id, @Param("expiresOn") LocalDate expiresOn,
                           @Param("threshold") int threshold);

    /** Whether any workspace brought its own app at {@code provider}: the one finder across workspaces, a yes/no. */
    boolean existsByProviderAndMode(IntegrationProvider provider, CredentialMode mode);
}
