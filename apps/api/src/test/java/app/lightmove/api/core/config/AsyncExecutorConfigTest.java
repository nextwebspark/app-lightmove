package app.lightmove.api.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Reads application.yml itself, so the pool tested is the one production runs. */
class AsyncExecutorConfigTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(AsyncExecutorConfig.class);

    @Test
    @DisplayName("a listener starts at once while long ones hold every core thread")
    void startsAtOnceBehindLongListeners() {
        contexts.run(context -> {
            ThreadPoolTaskExecutor pool = context.getBean("applicationTaskExecutor", ThreadPoolTaskExecutor.class);
            CountDownLatch release = new CountDownLatch(1);
            try {
                hold(pool, pool.getCorePoolSize(), release);
                CompletableFuture<Boolean> shortListener = new CompletableFuture<>();

                pool.execute(() -> shortListener.complete(true));

                assertThat(shortListener.get(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                release.countDown();
            }
        });
    }

    @Test
    @DisplayName("past the pool's last thread, a listener runs on the thread that published it")
    void runsOnItsPublisherPastThePool() {
        contexts.run(context -> {
            ThreadPoolTaskExecutor pool = context.getBean("applicationTaskExecutor", ThreadPoolTaskExecutor.class);
            CountDownLatch release = new CountDownLatch(1);
            try {
                hold(pool, pool.getMaxPoolSize(), release);
                CompletableFuture<Thread> ranOn = new CompletableFuture<>();

                pool.execute(() -> ranOn.complete(Thread.currentThread()));

                assertThat(ranOn.get(5, TimeUnit.SECONDS)).isSameAs(Thread.currentThread());
            } finally {
                release.countDown();
            }
        });
    }

    private static void hold(ThreadPoolTaskExecutor pool, int threads, CountDownLatch release) throws Exception {
        CountDownLatch holding = new CountDownLatch(threads);
        for (int listener = 0; listener < threads; listener++) {
            pool.execute(() -> {
                holding.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
