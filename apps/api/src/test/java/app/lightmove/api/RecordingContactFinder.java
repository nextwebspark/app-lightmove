package app.lightmove.api;

import app.lightmove.api.candidate.model.FoundEmails;
import app.lightmove.api.candidate.model.FoundPhones;
import app.lightmove.api.enrichment.contact.service.ContactFinder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A {@link ContactFinder} that answers whatever the test scripted and remembers what it was asked.
 *
 * <p>{@link #askedUrls()} is the point of it: this feature's central promise is that a value already
 * held is never bought twice, and the only way to prove that is to count the calls that were not made.
 */
public class RecordingContactFinder implements ContactFinder {

    private static final String SOURCE = "contactout";

    private final List<String> asked = new CopyOnWriteArrayList<>();
    private volatile FoundEmails emails = FoundEmails.none(SOURCE);
    private volatile FoundPhones phones = FoundPhones.none(SOURCE);
    private volatile RuntimeException failure;
    private volatile boolean offered = true;
    private volatile CyclicBarrier meeting;
    private volatile Runnable whileAsked;

    @Override
    public FoundEmails findEmails(String linkedinUrl) {
        asked.add(linkedinUrl);
        beforeAnswering();
        if (failure != null) {
            throw failure;
        }
        return emails;
    }

    @Override
    public FoundPhones findPhones(String linkedinUrl) {
        asked.add(linkedinUrl);
        beforeAnswering();
        if (failure != null) {
            throw failure;
        }
        return phones;
    }

    @Override
    public boolean isOffered() {
        return offered;
    }

    public void answerEmailsWith(FoundEmails found) {
        this.emails = found;
        this.failure = null;
    }

    public void answerPhonesWith(FoundPhones found) {
        this.phones = found;
        this.failure = null;
    }

    public void failWith(RuntimeException exception) {
        this.failure = exception;
    }

    /** Holds every call until {@code parties} are in flight at once, so a test can race presses past the guard. */
    public void answerTogether(int parties) {
        this.meeting = new CyclicBarrier(parties);
    }

    /** Runs {@code meanwhile} inside every call, as another press would while this one waits on the provider. */
    public void whileAsked(Runnable meanwhile) {
        this.whileAsked = meanwhile;
    }

    public void offer(boolean isOffered) {
        this.offered = isOffered;
    }

    public void clear() {
        asked.clear();
        emails = FoundEmails.none(SOURCE);
        phones = FoundPhones.none(SOURCE);
        failure = null;
        offered = true;
        meeting = null;
        whileAsked = null;
    }

    private void beforeAnswering() {
        Runnable meanwhile = whileAsked;
        if (meanwhile != null) {
            meanwhile.run();
        }
        CyclicBarrier barrier = meeting;
        if (barrier == null) {
            return;
        }
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception interrupted) {
            throw new IllegalStateException("the other presses never reached the provider", interrupted);
        }
    }

    public List<String> askedUrls() {
        return List.copyOf(asked);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /** {@code @Primary} so it wins over the LogContactFinder the application would pick. */
        @Bean
        @Primary
        public RecordingContactFinder recordingContactFinder() {
            return new RecordingContactFinder();
        }
    }
}
