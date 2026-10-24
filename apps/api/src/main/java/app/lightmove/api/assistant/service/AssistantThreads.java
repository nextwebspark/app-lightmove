package app.lightmove.api.assistant.service;

import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.logging.service.MdcPropagation;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Platform threads for an answer and its side-by-side tools, apart from the {@code @Async} pool: past that
 * pool's last thread an answer would run on the request thread, its stream unreturned until it finished. A
 * tool past this pool, or after shutdown, runs on its answer's thread. Not an {@code Executor} bean, which
 * would stand in for Spring Boot's own.
 */
@Component
class AssistantThreads {

    private final ThreadPoolExecutor answerThreads;
    private final ThreadPoolExecutor toolThreads;

    AssistantThreads(LightMoveProperties properties) {
        AssistantSettings settings = properties.assistant();
        int asks = settings.maxConcurrentAsks();
        this.answerThreads = new ThreadPoolExecutor(asks, asks, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                Thread.ofPlatform().daemon().name("assistant-answer-", 0).factory());
        this.answerThreads.allowCoreThreadTimeOut(true);
        this.toolThreads = new ThreadPoolExecutor(0, asks * settings.toolParallelism(), 60, TimeUnit.SECONDS,
                new SynchronousQueue<>(), Thread.ofPlatform().daemon().name("assistant-tool-", 0).factory(),
                (tool, pool) -> tool.run());
    }

    Executor answers() {
        return MdcPropagation.propagating(answerThreads);
    }

    Executor tools() {
        return MdcPropagation.propagating(toolThreads);
    }

    /** Never interrupts: an answer is paid for and saved after its stream ends, so it runs on while the instance does. */
    @PreDestroy
    void stop() {
        answerThreads.shutdown();
        toolThreads.shutdown();
    }
}
