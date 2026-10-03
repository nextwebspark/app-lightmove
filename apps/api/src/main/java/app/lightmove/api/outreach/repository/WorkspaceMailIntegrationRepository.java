package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.WorkspaceMailIntegration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** A workspace's OAuth app choices. Every finder takes the workspace id: the rows are tenant data. */
public interface WorkspaceMailIntegrationRepository extends JpaRepository<WorkspaceMailIntegration, UUID> {

    List<WorkspaceMailIntegration> findByWorkspaceId(UUID workspaceId);

    Optional<WorkspaceMailIntegration> findByWorkspaceIdAndProvider(UUID workspaceId, IntegrationProvider provider);

    /** Whether any workspace brought its own app at {@code provider}: the one finder across workspaces, a yes/no. */
    boolean existsByProviderAndMode(IntegrationProvider provider, CredentialMode mode);
}
