package app.lightmove.api.project.model;

import java.util.Set;
import java.util.UUID;

/** Which positions a member may see: every one of the workspace, or only those they hold a seat on. */
public record PositionVisibility(boolean every, Set<UUID> seated) {

    public static PositionVisibility everyPosition() {
        return new PositionVisibility(true, Set.of());
    }

    public static PositionVisibility seatedOn(Set<UUID> seated) {
        return new PositionVisibility(false, Set.copyOf(seated));
    }

    public boolean admits(UUID projectId) {
        return every || seated.contains(projectId);
    }
}
