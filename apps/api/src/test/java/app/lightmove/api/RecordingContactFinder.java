package app.lightmove.api;

import app.lightmove.api.candidate.model.FoundEmails;
import app.lightmove.api.candidate.model.FoundPhones;
import app.lightmove.api.enrichment.contact.service.ContactFinder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

    @Override
    public FoundEmails findEmails(String linkedinUrl) {
        asked.add(linkedinUrl);
        if (failure != null) {
            throw failure;
        }
        return emails;
    }

    @Override
    public FoundPhones findPhones(String linkedinUrl) {
        asked.add(linkedinUrl);
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

    public void offer(boolean isOffered) {
        this.offered = isOffered;
    }

    public void clear() {
        asked.clear();
        emails = FoundEmails.none(SOURCE);
        phones = FoundPhones.none(SOURCE);
        failure = null;
        offered = true;
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
