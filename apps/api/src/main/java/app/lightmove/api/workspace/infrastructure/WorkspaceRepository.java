package app.lightmove.api.workspace.infrastructure;

import app.lightmove.api.workspace.domain.Workspace;
import app.lightmove.api.workspace.domain.WorkspaceStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    /**
     * The workspaces already running on an email domain — the list a new signup is offered so they can
     * find their firm instead of accidentally starting a second copy of it.
     *
     * <p>Returns a list, not an Optional: several workspaces may share a domain, which is the point.
     * Callers must first check {@code EmailAddressValidator.canGroupColleaguesBy(domain)} — running this
     * for {@code gmail.com} would return every unrelated customer who signed up with Gmail.
     */
    List<Workspace> findByEmailDomainAndStatus(String emailDomain, WorkspaceStatus status);

    boolean existsBySlug(String slug);
}
