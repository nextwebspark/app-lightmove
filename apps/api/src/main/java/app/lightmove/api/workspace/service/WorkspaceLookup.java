package app.lightmove.api.workspace.service;

import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** How other modules read a workspace row: the workspace module's repository stays its own. */
@Service
@RequiredArgsConstructor
public class WorkspaceLookup {

    private final WorkspaceRepository workspaces;

    public Optional<Workspace> findById(UUID workspaceId) {
        return workspaces.findById(workspaceId);
    }

    public List<Workspace> findAllById(Iterable<UUID> workspaceIds) {
        return workspaces.findAllById(workspaceIds);
    }

    /** Roster changes wait their turn, so two admins stepping each other down cannot both count the other. */
    public Optional<Workspace> lockForRosterChange(UUID workspaceId) {
        return workspaces.lockForRosterChange(workspaceId);
    }
}
