package app.lightmove.api.core.tenant;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
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
     * Refused inside a transaction begun for another workspace: a transaction joined here keeps the connection, and
     * so the workspace, it began with, and the task would read the outer workspace's rows believing them this
     * one's. One begun for no workspace is let through — joined, it reads nothing, which fails closed — because an
     * after-commit listener still runs inside the committed transaction of a thread that had none.
     */
    public static <T> T call(UUID workspaceId, Supplier<T> task) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        UUID outer = TenantContext.currentWorkspaceId();
        if (TransactionSynchronizationManager.isActualTransactionActive() && outer != null
                && !workspaceId.equals(outer)) {
            throw new IllegalStateException("A workspace scope must be entered before its transaction begins");
        }
        return callBound(workspaceId, task);
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
