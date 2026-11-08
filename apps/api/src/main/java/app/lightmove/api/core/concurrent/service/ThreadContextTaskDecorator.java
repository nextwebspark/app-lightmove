package app.lightmove.api.core.concurrent.service;

import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

/**
 * Boot applies a lone {@link TaskDecorator} bean to {@code applicationTaskExecutor}, the executor every
 * {@code @Async} method runs on — so each one inherits the request's MDC and workspace. See
 * {@link ThreadContextPropagation}.
 */
@Component
public class ThreadContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return ThreadContextPropagation.wrap(runnable);
    }
}
