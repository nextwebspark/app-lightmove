package app.lightmove.api.core.logging.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

/** A task handed to another thread logs as the request that handed it over, and leaves nothing behind. */
class MdcPropagationTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("a wrapped task sees the submitting thread's MDC, and the pooled thread is empty after it")
    void carriesTheMdcAndLeavesThePooledThreadClean() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            MDC.put(CorrelationId.MDC_KEY, "abc123");
            MDC.put(CorrelationId.WORKSPACE_ID_KEY, "ws-1");

            Future<Map<String, String>> seen = pool.submit(MdcPropagation.wrap(MDC::getCopyOfContextMap));
            assertThat(seen.get()).containsEntry(CorrelationId.MDC_KEY, "abc123")
                    .containsEntry(CorrelationId.WORKSPACE_ID_KEY, "ws-1");

            MDC.clear();
            Future<Map<String, String>> afterwards = pool.submit(MDC::getCopyOfContextMap);
            assertThat(afterwards.get()).isNullOrEmpty();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("a task run on the submitting thread itself hands that thread's MDC back")
    void restoresTheCallersMdcWhenRunInline() {
        MDC.put(CorrelationId.MDC_KEY, "captured");
        Runnable task = MdcPropagation.wrap(() -> MDC.put("addedByTask", "x"));
        MDC.put(CorrelationId.MDC_KEY, "current");

        task.run();

        assertThat(MDC.getCopyOfContextMap()).containsExactly(Map.entry(CorrelationId.MDC_KEY, "current"));
    }

    @Test
    @DisplayName("an executor wrapped with propagating carries the MDC into supplyAsync")
    void propagatingExecutorCarriesTheMdc() {
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        try {
            MDC.put(CorrelationId.MDC_KEY, "abc123");

            String seen = CompletableFuture.supplyAsync(() -> MDC.get(CorrelationId.MDC_KEY),
                    MdcPropagation.propagating(pool)).join();

            assertThat(seen).isEqualTo("abc123");
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * The integration suite runs {@code @Async} inline ({@code SynchronousAuditWrites}), so it cannot see
     * whether Boot applied the decorator — and an inert decorator looks exactly like a working one.
     */
    @Test
    @DisplayName("Boot applies the decorator to the virtual-thread executor @Async runs on")
    void bootAppliesTheDecoratorToAsync() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
                .withPropertyValues("spring.threads.virtual.enabled=true")
                .withUserConfiguration(AsyncProbeConfig.class)
                .run(context -> {
                    MDC.put(CorrelationId.MDC_KEY, "abc123");
                    AsyncProbe probe = context.getBean(AsyncProbe.class);

                    ObservedOffThread seen = probe.observe().join();

                    assertThat(seen.thread()).isNotSameAs(Thread.currentThread());
                    assertThat(seen.correlationId()).isEqualTo("abc123");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    static class AsyncProbeConfig {

        @Bean
        MdcTaskDecorator mdcTaskDecorator() {
            return new MdcTaskDecorator();
        }

        @Bean
        AsyncProbe asyncProbe() {
            return new AsyncProbe();
        }
    }

    record ObservedOffThread(Thread thread, String correlationId) {
    }

    static class AsyncProbe {

        @Async
        public CompletableFuture<ObservedOffThread> observe() {
            return CompletableFuture.completedFuture(
                    new ObservedOffThread(Thread.currentThread(), MDC.get(CorrelationId.MDC_KEY)));
        }
    }
}
