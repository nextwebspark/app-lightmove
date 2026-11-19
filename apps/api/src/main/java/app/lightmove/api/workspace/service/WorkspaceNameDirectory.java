package app.lightmove.api.workspace.service;

import app.lightmove.api.core.security.oauth.WorkspaceNames;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class WorkspaceNameDirectory implements WorkspaceNames {

    private final WorkspaceRepository workspaces;

    @Override
    public Map<UUID, String> namesOf(Collection<UUID> workspaceIds) {
        return workspaces.findAllById(List.copyOf(workspaceIds)).stream()
                .collect(Collectors.toMap(Workspace::getId, Workspace::getName));
    }
}
