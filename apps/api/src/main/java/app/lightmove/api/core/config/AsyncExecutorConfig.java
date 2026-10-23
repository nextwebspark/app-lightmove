package app.lightmove.api.core.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Past the {@code @Async} pool's last thread a listener runs on the thread that published it, never refused:
 * the pool keeps no queue ({@code spring.task.execution}), and a dropped audit row is worse than a slower request.
 */
@Configuration(proxyBeanMethods = false)
public class AsyncExecutorConfig {

    @Bean
    ThreadPoolTaskExecutorCustomizer callerRunsPastTheLastThread() {
        return executor -> executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
