package app.uncava.api.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.uncava.api.core.config.AssistantSettings;
import app.uncava.api.core.config.UncavaProperties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModelCallThreadsTest {

    private final ModelCallThreads threads = threadsFor(1);

    @AfterEach
    void stop() {
        threads.stop();
    }

    @Test
    @DisplayName("a model call waits on a platform thread")
    void runsAModelCallOnAPlatformThread() throws Exception {
        CompletableFuture<Boolean> onVirtualThread = new CompletableFuture<>();

        Thread.ofVirtual().start(() ->
                threads.execute(() -> onVirtualThread.complete(Thread.currentThread().isVirtual()))).join();

        assertThat(onVirtualThread.get(5, TimeUnit.SECONDS)).isFalse();
    }

    @Test
    @DisplayName("once every attempt of every admitted ask holds a thread, a call runs on its caller")
    void runsOnTheCallerOnceEveryThreadIsHeld() throws Exception {
        CountDownLatch held = new CountDownLatch(AssistantRounds.ATTEMPTS_PER_ROUND);
        CountDownLatch release = new CountDownLatch(1);
        for (int call = 0; call < AssistantRounds.ATTEMPTS_PER_ROUND; call++) {
            threads.execute(() -> {
                held.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Thread> ranOn = new CompletableFuture<>();

        threads.execute(() -> ranOn.complete(Thread.currentThread()));
        release.countDown();

        assertThat(ranOn.get(5, TimeUnit.SECONDS)).isSameAs(Thread.currentThread());
    }

    private static ModelCallThreads threadsFor(int concurrentAsks) {
        AssistantSettings settings = mock(AssistantSettings.class);
        when(settings.maxConcurrentAsks()).thenReturn(concurrentAsks);
        UncavaProperties properties = mock(UncavaProperties.class);
        when(properties.assistant()).thenReturn(settings);
        return new ModelCallThreads(properties);
    }
}
