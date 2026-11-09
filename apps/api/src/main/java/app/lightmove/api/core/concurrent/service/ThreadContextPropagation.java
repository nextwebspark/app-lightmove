package app.lightmove.api.core.concurrent.service;

import app.lightmove.api.core.logging.service.MdcPropagation;
import app.lightmove.api.core.tenant.TenantContext;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * Hands a task to another thread with what the submitting thread runs under: its MDC, so the task logs as the request
 * that handed it over, and its workspace ({@link TenantContext}), without which the task reads none of the protected
 * tables. {@link ThreadContextTaskDecorator} applies it to {@code @Async}; a pool built by hand must wrap its own tasks.
 */
public final class ThreadContextPropagation {

    private ThreadContextPropagation() {
    }

    public static Runnable wrap(Runnable task) {
        return MdcPropagation.wrap(TenantContext.propagating(task));
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        return MdcPropagation.wrap(TenantContext.propagating(task));
    }

    public static Executor propagating(Executor executor) {
        return task -> executor.execute(wrap(task));
    }
}
