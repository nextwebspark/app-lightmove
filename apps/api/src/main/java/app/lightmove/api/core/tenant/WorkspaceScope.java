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
        return enter(workspaceId, task, false);
    }

    /**
     * The deliberate exception to entering a scope before its transaction: an account's work that reaches past the
     * caller's workspace and must commit with the change behind it — founding a workspace (its trial is written in the
     * founding transaction) and a changed password ending the account's AI-app grants in every workspace. The joined
     * connection reads {@code workspaceId} for the task and the caller's again after, so anything the task leaves to
     * the session's flush must be flushed inside it.
     */
    public static void runInJoinedTransaction(UUID workspaceId, Runnable task) {
        enter(workspaceId, () -> {
            task.run();
            return null;
        }, true);
    }

    private static <T> T enter(UUID workspaceId, Supplier<T> task, boolean joinedOnPurpose) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        UUID outer = TenantContext.currentWorkspaceId();
        Connection joined = boundConnection();
        if (joined == null || workspaceId.equals(outer)) {
            return callBound(workspaceId, task);
        }
        if (!joinedOnPurpose && outer != null && TransactionSynchronizationManager.isActualTransactionActive()) {
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
