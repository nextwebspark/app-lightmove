package app.lightmove.api.project.model;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * Which mandates of one workspace to count executives for, and as whom: one the caller reads as a
 * client seat counts only the executives shown to the client.
 */
@NamedInterface(name = "api", propagate = false)
public record CandidateCountScope(UUID workspaceId, Collection<UUID> projectIds, Set<UUID> readAsClient) {

    public List<UUID> countedWhole() {
        return projectIds.stream().filter(projectId -> !readAsClient.contains(projectId)).toList();
    }

    public List<UUID> countedClientVisibleOnly() {
        return projectIds.stream().filter(readAsClient::contains).toList();
    }
}
