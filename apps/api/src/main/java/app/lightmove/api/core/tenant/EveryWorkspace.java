package app.lightmove.api.core.tenant;

import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * A scheduled job's walk over the tenants: the work for each workspace runs in that workspace's scope, because one
 * statement over every workspace's rows is exactly what row-level security refuses. A workspace that fails is logged
 * and the rest still run, so one bad tenant never starves the ones after it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EveryWorkspace {

    private final JdbcTemplate jdbc;

    /** Every workspace: {@code app_lm_workspace} carries no policy, so the list is always whole. */
    public int sum(ToIntFunction<UUID> perWorkspace) {
        return sumOver(jdbc.queryForList("SELECT id FROM app_lm_workspace ORDER BY id", UUID.class), perWorkspace);
    }

    /**
     * Only the workspaces a job's own table names as having work, so an idle tenant costs no transaction. The query
     * must read a table row-level security does not protect, or it answers nothing outside a scope.
     */
    public int sumWithWork(String workspaceIdsWithWork, ToIntFunction<UUID> perWorkspace, Object... args) {
        return sumOver(jdbc.queryForList(workspaceIdsWithWork, UUID.class, args), perWorkspace);
    }

    private int sumOver(List<UUID> workspaceIds, ToIntFunction<UUID> perWorkspace) {
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
