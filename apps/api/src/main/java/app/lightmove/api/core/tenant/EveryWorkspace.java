package app.lightmove.api.core.tenant;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * A scheduled job's walk over the tenants: the work for each workspace runs in that workspace's scope, because one
 * statement over every workspace's rows is exactly what row-level security refuses. A workspace that fails is logged
 * and the rest still run, so one bad tenant never starves the ones after it. A frequent job walks only the workspaces
 * {@link TenantResolvers} names as having its work, so an idle tenant costs it no transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EveryWorkspace {

    private final JdbcTemplate jdbc;

    /** Every workspace: {@code app_lm_workspace} carries no policy, so the list is always whole. */
    public List<UUID> ids() {
        return jdbc.queryForList("SELECT id FROM app_lm_workspace ORDER BY id", UUID.class);
    }

    public void forEach(Consumer<UUID> perWorkspace) {
        forEach(ids(), perWorkspace);
    }

    public int sum(ToIntFunction<UUID> perWorkspace) {
        return sum(ids(), perWorkspace);
    }

    public void forEach(Collection<UUID> workspaceIds, Consumer<UUID> perWorkspace) {
        sum(workspaceIds, workspaceId -> {
            perWorkspace.accept(workspaceId);
            return 0;
        });
    }

    public int sum(Collection<UUID> workspaceIds, ToIntFunction<UUID> perWorkspace) {
        int total = 0;
        for (UUID workspaceId : workspaceIds) {
            try {
                total += WorkspaceScope.call(workspaceId, () -> perWorkspace.applyAsInt(workspaceId));
            } catch (RuntimeException failed) {
                log.error("Workspace {} failed its pass; the rest still run", workspaceId, failed);
            }
        }
        return total;
    }
}
