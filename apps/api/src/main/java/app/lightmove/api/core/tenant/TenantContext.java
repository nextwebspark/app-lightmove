package app.lightmove.api.core.tenant;

import java.util.UUID;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Which workspace the running thread works for: one a {@link WorkspaceScope} named, or else the signed-in caller's —
 * never a request parameter. {@link TenantAwareDataSource} hands it to every connection this thread borrows.
 */
public final class TenantContext {

    private static final ThreadLocal<@Nullable UUID> SCOPED = new ThreadLocal<>();

    private TenantContext() {
    }

    public static @Nullable UUID currentWorkspaceId() {
        UUID scoped = SCOPED.get();
        return scoped != null ? scoped : principalWorkspaceId();
    }

    private static @Nullable UUID principalWorkspaceId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TenantPrincipal principal) {
            return principal.workspaceId();
        }
        return null;
    }

    /**
     * Runs a task with a workspace bound, restoring whatever the thread held before: a task may run on the thread
     * that handed it over (caller-runs), and clearing would strip that request of its own.
     */
    static <T> T callBound(@Nullable UUID workspaceId, Callable<T> task) throws Exception {
        UUID previous = SCOPED.get();
        SCOPED.set(workspaceId);
        try {
            return task.call();
        } finally {
            if (previous == null) {
                SCOPED.remove();
            } else {
                SCOPED.set(previous);
            }
        }
    }

    /** A task that runs on another thread for the workspace this thread works for now. */
    public static Runnable propagating(Runnable task) {
        UUID submitted = currentWorkspaceId();
        return () -> WorkspaceScope.runBound(submitted, task);
    }

    public static <T> Callable<T> propagating(Callable<T> task) {
        UUID submitted = currentWorkspaceId();
        return () -> callBound(submitted, task);
    }
}
