package app.lightmove.api;

import app.lightmove.api.assistant.model.AssistantAnswer;
import app.lightmove.api.assistant.model.AssistantTurnPrompt;
import app.lightmove.api.assistant.service.AssistantEventSink;
import app.lightmove.api.assistant.service.AssistantTurnRunner;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A turn runner a test can script, hold open, or make fail.
 *
 * <p>Takes over from {@code StubChatModel} for the assistant's own suites. The stub answers at the
 * model layer, which is enough while a turn is one call; a turn that now runs asynchronously needs a
 * test to be able to <b>stop time</b> — to open a stream while the turn is genuinely in flight, and
 * to assert what a failure leaves behind. {@code GeminiAssistantTurnRunner} keeps its own unit test,
 * so nothing loses coverage by this standing in.
 */
public class RecordingAssistantTurnRunner implements AssistantTurnRunner {

    private final List<String> questions = new CopyOnWriteArrayList<>();

    private volatile List<String> deltas = List.of("stubbed response");
    private volatile RuntimeException failure;
    private volatile CountDownLatch gate;

    /** Splits its answer across these, so a test can watch text arrive in pieces. */
    public void answering(String... chunks) {
        this.deltas = List.of(chunks);
        this.failure = null;
    }

    public void failingWith(RuntimeException failure) {
        this.failure = failure;
    }

    /**
     * Holds every turn until {@link #release()}. The turn is RUNNING and observable meanwhile, which
     * is the only way to test a live stream rather than a replay.
     */
    public CountDownLatch gate() {
        this.gate = new CountDownLatch(1);
        return gate;
    }

    public void release() {
        CountDownLatch held = gate;
        if (held != null) {
            held.countDown();
        }
    }

    public List<String> questions() {
        return questions;
    }

    public void clear() {
        questions.clear();
        deltas = List.of("stubbed response");
        failure = null;
        release();
        gate = null;
    }

    @Override
    public AssistantAnswer run(AssistantTurnPrompt prompt, AssistantEventSink sink) {
        questions.add(prompt.question());
        await();
        if (failure != null) {
            // Emitted before throwing on purpose: a turn that dies halfway should read back as the
            // half it produced, not as nothing.
            deltas.forEach(sink::delta);
            throw failure;
        }
        deltas.forEach(sink::delta);
        return new AssistantAnswer(String.join("", deltas), "recording-runner", null, null);
    }

    private void await() {
        CountDownLatch held = gate;
        if (held == null) {
            return;
        }
        try {
            if (!held.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("A gated assistant turn was never released");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public RecordingAssistantTurnRunner recordingAssistantTurnRunner() {
            return new RecordingAssistantTurnRunner();
        }
    }
}
