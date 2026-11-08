package app.lightmove.api.core.concurrent.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.logging.service.CorrelationId;
import app.lightmove.api.core.tenant.TenantContext;
import app.lightmove.api.core.tenant.WorkspaceScope;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

/** A task handed to another thread works for the workspace, and logs as the request, that handed it over. */
class ThreadContextPropagationTest {

    private static final UUID WORKSPACE = UUID.randomUUID();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("a wrapped task runs in the submitter's workspace, and the pooled thread holds none after it")
    void carriesTheWorkspaceAndLeavesThePooledThreadClean() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            ObservedOffThread seen = WorkspaceScope.call(WORKSPACE, () -> {
                MDC.put(CorrelationId.MDC_KEY, "abc123");
                return CompletableFuture.supplyAsync(ObservedOffThread::now,
                        ThreadContextPropagation.propagating(pool)).join();
            });

            assertThat(seen.thread()).isNotSameAs(Thread.currentThread());
            assertThat(seen.workspaceId()).isEqualTo(WORKSPACE);
            assertThat(seen.correlationId()).isEqualTo("abc123");
            assertThat(pool.submit(TenantContext::currentWorkspaceId).get()).isNull();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("a task run on the submitting thread itself hands that thread's workspace back")
    void restoresTheCallersWorkspaceWhenRunInline() {
        UUID current = UUID.randomUUID();
        UUID[] seenByTask = new UUID[1];
        Runnable recordWorkspace = () -> seenByTask[0] = TenantContext.currentWorkspaceId();
        Runnable task = WorkspaceScope.call(WORKSPACE, () -> ThreadContextPropagation.wrap(recordWorkspace));

        UUID afterTask = WorkspaceScope.call(current, () -> {
            task.run();
            return TenantContext.currentWorkspaceId();
        });

        assertThat(seenByTask[0]).isEqualTo(WORKSPACE);
        assertThat(afterTask).isEqualTo(current);
    }

    /**
     * The integration suite runs {@code @Async} inline ({@code SynchronousAuditWrites}), so it cannot see
     * whether Boot applied the decorator — and an inert decorator looks exactly like a working one.
     */
    @Test
    @DisplayName("Boot applies the decorator to the pooled executor @Async runs on")
    void bootAppliesTheDecoratorToAsync() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
                .withPropertyValues("spring.threads.virtual.enabled=false")
                .withUserConfiguration(AsyncProbeConfig.class)
                .run(context -> {
                    AsyncProbe probe = context.getBean(AsyncProbe.class);

                    ObservedOffThread seen = WorkspaceScope.call(WORKSPACE, () -> {
                        MDC.put(CorrelationId.MDC_KEY, "abc123");
                        return probe.observe().join();
                    });

                    assertThat(seen.thread()).isNotSameAs(Thread.currentThread());
                    assertThat(seen.correlationId()).isEqualTo("abc123");
                    assertThat(seen.workspaceId()).isEqualTo(WORKSPACE);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    static class AsyncProbeConfig {

        @Bean
        ThreadContextTaskDecorator threadContextTaskDecorator() {
            return new ThreadContextTaskDecorator();
        }

        @Bean
        AsyncProbe asyncProbe() {
            return new AsyncProbe();
        }
    }

    record ObservedOffThread(Thread thread, String correlationId, UUID workspaceId) {

        static ObservedOffThread now() {
            return new ObservedOffThread(Thread.currentThread(), MDC.get(CorrelationId.MDC_KEY),
                    TenantContext.currentWorkspaceId());
        }
    }

    static class AsyncProbe {

        @Async
        public CompletableFuture<ObservedOffThread> observe() {
            return CompletableFuture.completedFuture(ObservedOffThread.now());
        }
    }
}
