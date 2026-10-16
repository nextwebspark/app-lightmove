package app.uncava.api.assistant.service;

import app.uncava.api.core.config.UncavaProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * The platform threads the assistant's model calls wait on. On a virtual thread (Java 21) about one
 * round in three sat in a socket read for an answer Vertex had already sent. Held to every attempt of
 * every ask an instance admits; past that an attempt runs on its caller. Not an {@code Executor} bean,
 * which would stand in for Spring Boot's own.
 */
@Component
class ModelCallThreads {

    private final ThreadPoolExecutor threads;

    ModelCallThreads(UncavaProperties properties) {
        int most = properties.assistant().maxConcurrentAsks() * AssistantRounds.ATTEMPTS_PER_ROUND;
        this.threads = new ThreadPoolExecutor(0, most, 60, TimeUnit.SECONDS, new SynchronousQueue<>(),
                Thread.ofPlatform().daemon().name("assistant-model-", 0).factory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    void execute(Runnable modelCall) {
        threads.execute(modelCall);
    }

    @PreDestroy
    void stop() {
        threads.shutdownNow();
    }
}
