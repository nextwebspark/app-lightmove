package app.uncava.api.project.model;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Which mandates of one workspace to count executives for, and as whom: one the caller reads as a
 * client seat counts only the executives shown to the client.
 */
public record CandidateCountScope(UUID workspaceId, Collection<UUID> projectIds, Set<UUID> readAsClient) {

    public List<UUID> countedWhole() {
        return projectIds.stream().filter(projectId -> !readAsClient.contains(projectId)).toList();
    }

    public List<UUID> countedClientVisibleOnly() {
        return projectIds.stream().filter(readAsClient::contains).toList();
    }
}
