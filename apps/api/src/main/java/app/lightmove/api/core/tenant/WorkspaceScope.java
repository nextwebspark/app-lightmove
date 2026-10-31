package app.lightmove.api.core.tenant;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Work for one workspace that no signed-in request carries — a scheduled job's item, a webhook once its row names
 * the workspace, a door that acts on a workspace other than the caller's. Every connection borrowed inside reads and
 * writes that workspace's rows and no other's.
 */
public final class WorkspaceScope {

    private WorkspaceScope() {
    }

    public static void run(UUID workspaceId, Runnable task) {
        call(workspaceId, () -> {
            task.run();
            return null;
        });
    }

    /**
     * Refused inside a live transaction begun for another workspace: the task's reads would join it, on the outer
     * workspace's connection. A connection still bound with no live transaction — an after-commit listener run on
     * the publishing thread, which Spring reports inactive while the committed transaction's connection is still
     * what a read joins — and one bound for no workspace are set to this workspace for the task and back after.
     */
    public static <T> T call(UUID workspaceId, Supplier<T> task) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        UUID outer = TenantContext.currentWorkspaceId();
        Connection joined = boundConnection();
        if (joined == null || workspaceId.equals(outer)) {
            return callBound(workspaceId, task);
        }
        if (outer != null && TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A workspace scope must be entered before its transaction begins");
        }
        setOn(joined, workspaceId);
        try {
            return callBound(workspaceId, task);
        } finally {
            setOn(joined, outer);
        }
    }

    private static @Nullable Connection boundConnection() {
        for (Object resource : TransactionSynchronizationManager.getResourceMap().values()) {
            if (resource instanceof ConnectionHolder holder && holder.getConnectionHandle() != null) {
                return holder.getConnection();
            }
        }
        return null;
    }

    private static void setOn(Connection connection, @Nullable UUID workspaceId) {
        try {
            TenantAwareDataSource.setWorkspace(connection, workspaceId);
        } catch (SQLException failed) {
            throw new CannotCreateTransactionException("Could not set the workspace on the joined connection", failed);
        }
    }

    static void runBound(@Nullable UUID workspaceId, Runnable task) {
        callBound(workspaceId, () -> {
            task.run();
            return null;
        });
    }

    private static <T> T callBound(@Nullable UUID workspaceId, Supplier<T> task) {
        try {
            return TenantContext.callBound(workspaceId, task::get);
        } catch (RuntimeException | Error unchecked) {
            throw unchecked;
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
