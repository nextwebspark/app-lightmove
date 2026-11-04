package app.lightmove.api.core.logging.service;

import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

/**
 * Boot applies a lone {@link TaskDecorator} bean to {@code applicationTaskExecutor}, the executor every
 * {@code @Async} method runs on — so each one inherits the request's MDC. See {@link MdcPropagation}.
 */
@Component
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return MdcPropagation.wrap(runnable);
    }
}
