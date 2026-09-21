package app.lightmove.api.assistant.config;

import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The assistant's own thread pools.
 *
 * <p>Named beans rather than {@code @Async} with no qualifier, and <b>platform threads rather than
 * virtual ones</b>. {@code spring.threads.virtual.enabled: true} means the application's default
 * executor is unbounded with no queue limit and no rejection policy — which is the right answer for
 * short IO-bound request work and the wrong one here, where the whole point is a bound. Every other
 * {@code @Async} site in the application takes no qualifier and keeps resolving to Boot's
 * {@code applicationTaskExecutor}, so adding these changes nothing for them.
 */
@Configuration
public class AssistantExecutorConfig {

    /**
     * Runs turns. <b>This pool is the spend cap as much as the concurrency cap.</b>
     *
     * <p>Two per instance against {@code --max-instances 2} is four concurrent Vertex calls
     * fleet-wide — a number you can reason about on a bill, and comfortably inside five database
     * connections even counting the appender's short writes. Raise
     * {@code lightmove.assistant.max-concurrent-turns} only together with the pool and the DB tier.
     *
     * <p>{@code AbortPolicy}, never {@code CallerRunsPolicy}: the caller is the Tomcat thread that
     * owes the browser a 202, and running a 90-second turn on it would reintroduce exactly the
     * timeout this design exists to avoid. The rejection surfaces on the request thread, which is
     * what lets the accept path answer 503 and settle the row instead of stranding it.
     */
    @Bean("assistantTurnExecutor")
    public ThreadPoolTaskExecutor assistantTurnExecutor(LightMoveProperties properties) {
        AssistantSettings settings = properties.assistant();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(settings.maxConcurrentTurns());
        executor.setMaxPoolSize(settings.maxConcurrentTurns());
        executor.setQueueCapacity(settings.queueCapacity());
        executor.setThreadNamePrefix("assistant-turn-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // A Cloud Run SIGTERM should let an in-flight turn settle rather than strand a RUNNING row
        // for the sweep to reclaim — the answer may already be paid for.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }

    /**
     * Drains SSE subscribers, off the single {@code LISTEN} thread.
     *
     * <p>Small and bounded because each drain borrows a connection from a pool of five: a burst of
     * viewers must queue rather than starve the database. A rejected drain is safe to discard — the
     * next append schedules another and the client's ~55s reconnect replays from its cursor — so this
     * pool prefers dropping work to growing.
     */
    @Bean("assistantStreamExecutor")
    public ThreadPoolTaskExecutor assistantStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("assistant-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // A drain in flight holds an emitter and a connection; let it finish rather than tearing the
        // socket down mid-frame on a Cloud Run SIGTERM.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }
}
