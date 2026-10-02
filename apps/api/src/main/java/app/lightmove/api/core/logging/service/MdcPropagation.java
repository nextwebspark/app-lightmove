package app.lightmove.api.core.logging.service;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import org.slf4j.MDC;

/**
 * Carries the submitting thread's MDC into a task run on another thread. The MDC is a
 * {@code ThreadLocal}, so without this an audit write or a sourcing run logs with no correlation id,
 * no tenant and no trace, and belongs to nobody.
 *
 * <p>The whole map is copied, trace keys included, so a line written after the response was sent
 * still nests under the request that caused it. {@link MdcTaskDecorator} applies it to Spring's
 * executor; a pool built by hand must wrap its own tasks.
 */
public final class MdcPropagation {

    private MdcPropagation() {
    }

    public static Runnable wrap(Runnable task) {
        Map<String, String> submitted = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = install(submitted);
            try {
                task.run();
            } finally {
                install(previous);
            }
        };
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, String> submitted = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = install(submitted);
            try {
                return task.call();
            } finally {
                install(previous);
            }
        };
    }

    /** An executor whose every task runs with the MDC of the thread that handed it over. */
    public static Executor propagating(Executor executor) {
        return task -> executor.execute(wrap(task));
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
}
