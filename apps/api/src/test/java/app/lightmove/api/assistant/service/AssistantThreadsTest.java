package app.lightmove.api.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.logging.service.CorrelationId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class AssistantThreadsTest {

    private final List<AssistantThreads> started = new ArrayList<>();

    @AfterEach
    void stop() {
        started.forEach(AssistantThreads::stop);
        MDC.clear();
    }

    @Test
    @DisplayName("every admitted ask is answered at once, on a platform thread, under the asking request's MDC")
    void everyAdmittedAskRunsAtOnce() throws Exception {
        AssistantThreads threads = threadsFor(3, 1);
        CountDownLatch running = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        List<CompletableFuture<String>> seen = new ArrayList<>();
        MDC.put(CorrelationId.MDC_KEY, "abc123");

        for (int ask = 0; ask < 3; ask++) {
            CompletableFuture<String> answer = new CompletableFuture<>();
            seen.add(answer);
            threads.answers().execute(() -> {
                running.countDown();
                awaitQuietly(release);
                answer.complete(Thread.currentThread().isVirtual() + ":" + MDC.get(CorrelationId.MDC_KEY));
            });
        }
        assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
        release.countDown();

        for (CompletableFuture<String> answer : seen) {
            assertThat(answer.get(5, TimeUnit.SECONDS)).isEqualTo("false:abc123");
        }
    }

    @Test
    @DisplayName("answers waiting on their side-by-side tools never queue those tools behind each other")
    void answersWaitingOnToolsAllFinish() throws Exception {
        AssistantThreads threads = threadsFor(2, 2);
        CountDownLatch everyToolRunning = new CountDownLatch(4);
        List<CompletableFuture<Boolean>> answers = new ArrayList<>();

        for (int ask = 0; ask < 2; ask++) {
            CompletableFuture<Boolean> answered = new CompletableFuture<>();
            answers.add(answered);
            threads.answers().execute(() -> {
                List<CompletableFuture<Boolean>> tools = List.of(
                        CompletableFuture.supplyAsync(() -> meet(everyToolRunning), threads.tools()),
                        CompletableFuture.supplyAsync(() -> meet(everyToolRunning), threads.tools()));
                answered.complete(tools.stream().allMatch(CompletableFuture::join));
            });
        }

        for (CompletableFuture<Boolean> answered : answers) {
            assertThat(answered.get(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("once every tool thread is held, a tool runs on its answer's own thread")
    void toolPastThePoolRunsOnItsCaller() throws Exception {
        AssistantThreads threads = threadsFor(1, 1);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        threads.tools().execute(() -> {
            held.countDown();
            awaitQuietly(release);
        });
        assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Thread> ranOn = new CompletableFuture<>();

        threads.tools().execute(() -> ranOn.complete(Thread.currentThread()));
        release.countDown();

        assertThat(ranOn.get(5, TimeUnit.SECONDS)).isSameAs(Thread.currentThread());
    }

    @Test
    @DisplayName("stopping lets an answer already running finish, its tools included, rather than interrupting it")
    void stopLetsARunningAnswerFinish() throws Exception {
        AssistantThreads threads = threadsFor(1, 1);
        CountDownLatch running = new CountDownLatch(1);
        CompletableFuture<Boolean> saved = new CompletableFuture<>();
        threads.answers().execute(() -> {
            running.countDown();
            try {
                Thread.sleep(200);
                saved.complete(CompletableFuture.supplyAsync(() -> true, threads.tools()).join());
            } catch (InterruptedException interrupted) {
                saved.complete(false);
            }
        });
        assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();

        threads.stop();

        assertThat(saved.getNow(false)).isTrue();
    }

    private AssistantThreads threadsFor(int concurrentAsks, int toolParallelism) {
        AssistantSettings settings = mock(AssistantSettings.class);
        when(settings.maxConcurrentAsks()).thenReturn(concurrentAsks);
        when(settings.toolParallelism()).thenReturn(toolParallelism);
        LightMoveProperties properties = mock(LightMoveProperties.class);
        when(properties.assistant()).thenReturn(settings);
        AssistantThreads threads = new AssistantThreads(properties);
        started.add(threads);
        return threads;
    }

    private static boolean meet(CountDownLatch everyone) {
        everyone.countDown();
        return awaitQuietly(everyone);
    }

    private static boolean awaitQuietly(CountDownLatch latch) {
        try {
            return latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
