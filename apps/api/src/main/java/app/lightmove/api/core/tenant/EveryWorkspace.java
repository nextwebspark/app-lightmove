package app.lightmove.api.core.tenant;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * A scheduled job's walk over the tenants: the work for each workspace runs in that workspace's scope, because one
 * statement over every workspace's rows is exactly what row-level security refuses. {@code app_lm_workspace} itself
 * carries no policy, so the list is always whole.
 */
@Component
@RequiredArgsConstructor
public class EveryWorkspace {

    private final JdbcTemplate jdbc;

    public List<UUID> ids() {
        return jdbc.queryForList("SELECT id FROM app_lm_workspace ORDER BY id", UUID.class);
    }

    public void forEach(Consumer<UUID> perWorkspace) {
        for (UUID workspaceId : ids()) {
            WorkspaceScope.run(workspaceId, () -> perWorkspace.accept(workspaceId));
        }
    }

    public int sum(ToIntFunction<UUID> perWorkspace) {
        int total = 0;
        for (UUID workspaceId : ids()) {
            total += WorkspaceScope.call(workspaceId, () -> perWorkspace.applyAsInt(workspaceId));
        }
        return total;
    }
}
