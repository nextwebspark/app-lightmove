package app.lightmove.api.core.tenant;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Work on the rows that belong to no workspace (V192): the shared template library, which every session reads but
 * only this scope writes, and an OAuth grant still waiting for its consent, which only this scope reads or writes. A
 * firm's code path that loads a library template and changes it by mistake is refused by the database, not flushed.
 */
public final class SharedRows {

    private static final ThreadLocal<Boolean> OPEN = new ThreadLocal<>();

    private SharedRows() {
    }

    public static void run(Runnable task) {
        call(() -> {
            task.run();
            return null;
        });
    }

    /** A connection already bound (a joined transaction) is opened for the task and closed again after. */
    public static <T> T call(Supplier<T> task) {
        if (isOpen()) {
            return task.get();
        }
        Connection joined = WorkspaceScope.boundConnection();
        OPEN.set(Boolean.TRUE);
        T result;
        try {
            if (joined != null) {
                setOn(joined, true);
            }
            result = task.get();
        } catch (RuntimeException | Error failed) {
            OPEN.remove();
            if (joined != null) {
                try {
                    setOn(joined, false);
                } catch (RuntimeException restoreFailed) {
                    failed.addSuppressed(restoreFailed);
                }
            }
            throw failed;
        }
        OPEN.remove();
        if (joined != null) {
            setOn(joined, false);
        }
        return result;
    }

    static boolean isOpen() {
        return OPEN.get() != null;
    }

    private static void setOn(Connection connection, boolean open) {
        try {
            TenantAwareDataSource.setSharedRows(connection, open);
        } catch (SQLException failed) {
            throw new CannotCreateTransactionException("Could not set the shared-rows flag on the joined connection",
                    failed);
        }
    }
}
