package app.lightmove.api.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** A job's walk over the workspaces: each in its own scope, and one that fails stops none after it. */
class EveryWorkspaceTest {

    private final UUID failing = UUID.randomUUID();
    private final UUID healthy = UUID.randomUUID();

    @Test
    @DisplayName("a workspace that throws is skipped and the ones after it still run, each in its own scope")
    void oneWorkspaceFailingStopsNoneAfterIt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(UUID.class))).thenReturn(List.of(failing, healthy));
        List<UUID> scopedTo = new ArrayList<>();

        int total = new EveryWorkspace(jdbc).sum(workspaceId -> {
            scopedTo.add(TenantContext.currentWorkspaceId());
            if (workspaceId.equals(failing)) {
                throw new IllegalStateException("lock timeout");
            }
            return 3;
        });

        assertThat(total).isEqualTo(3);
        assertThat(scopedTo).containsExactly(failing, healthy);
        assertThat(TenantContext.currentWorkspaceId()).isNull();
    }

    @Test
    @DisplayName("sumWithWork walks only the workspaces its query names")
    void walksOnlyTheWorkspacesWithWork() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList("workspaces with work", UUID.class, "due")).thenReturn(List.of(healthy));
        List<UUID> walked = new ArrayList<>();

        int total = new EveryWorkspace(jdbc).sumWithWork("workspaces with work", workspaceId -> {
            walked.add(workspaceId);
            return 1;
        }, "due");

        assertThat(total).isEqualTo(1);
        assertThat(walked).containsExactly(healthy);
    }
}
