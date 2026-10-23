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
 * Platform threads for an answer and its side-by-side tools, apart from Spring's fixed, queued executor,
 * where an answer's tools could wait behind other answers until its deadline. A tool past the pool runs on
 * its answer's thread. Not an {@code Executor} bean, which would stand in for Spring Boot's own.
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
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    Executor answers() {
        return MdcPropagation.propagating(answerThreads);
    }

    Executor tools() {
        return MdcPropagation.propagating(toolThreads);
    }

    @PreDestroy
    void stop() {
        answerThreads.shutdownNow();
        toolThreads.shutdownNow();
    }
}
