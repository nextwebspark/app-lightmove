package app.lightmove.api.core.logging.service;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import org.slf4j.MDC;

/**
 * Carries the submitting thread's whole MDC into a task run on another thread. {@link MdcTaskDecorator}
 * applies it to Spring's executor; a pool built by hand must wrap its own tasks.
 */
public final class MdcPropagation {

    private MdcPropagation() {
    }

    public static Runnable wrap(Runnable task) {
        Map<String, String> submitted = MDC.getCopyOfContextMap();
        return () -> runWith(submitted, () -> {
            task.run();
            return null;
        });
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, String> submitted = MDC.getCopyOfContextMap();
        return () -> runWith(submitted, task::call);
    }

    /** An executor whose every task runs with the MDC of the thread that handed it over. */
    public static Executor propagating(Executor executor) {
        return task -> executor.execute(wrap(task));
    }

    private static <T, E extends Exception> T runWith(Map<String, String> context, MdcScopedTask<T, E> task) throws E {
        Map<String, String> previous = install(context);
        try {
            return task.run();
        } finally {
            install(previous);
        }
    }

    /**
     * Restores what the running thread had rather than clearing it: a task can run on the submitting
     * thread itself (caller-runs, an already-completed future), and clearing would wipe that request's MDC.
     */
    private static Map<String, String> install(Map<String, String> context) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
        return previous;
    }

    /** A {@code Runnable} declares no checked exception and a {@code Callable} declares any; this carries both. */
    @FunctionalInterface
    private interface MdcScopedTask<T, E extends Exception> {
        T run() throws E;
    }
}
