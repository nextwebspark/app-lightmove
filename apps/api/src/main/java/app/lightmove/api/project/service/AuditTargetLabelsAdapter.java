package app.lightmove.api.project.service;

import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.workspace.service.AuditTargetLabels;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Position titles and client names for the workspace's audit log. */
@Component
@RequiredArgsConstructor
class AuditTargetLabelsAdapter implements AuditTargetLabels {

    private final ProjectRepository projects;
    private final ClientRepository clients;

    @Override
    public Map<UUID, String> positionTitles(UUID workspaceId, Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return projects.findAllById(projectIds).stream()
                .filter(project -> workspaceId.equals(project.getWorkspaceId()) && project.getPositionTitle() != null)
                .collect(Collectors.toMap(Project::getId, Project::getPositionTitle));
    }

    @Override
    public Map<UUID, String> clientNames(UUID workspaceId, Collection<UUID> clientIds) {
        if (clientIds.isEmpty()) {
            return Map.of();
        }
        return clients.findAllById(clientIds).stream()
                .filter(client -> workspaceId.equals(client.getWorkspaceId()) && client.getName() != null)
                .collect(Collectors.toMap(Client::getId, Client::getName));
    }
}
