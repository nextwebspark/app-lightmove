package app.lightmove.api;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.MailboxEvent;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.model.SentEmail;
import app.lightmove.api.outreach.service.MailboxGateway;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * A {@link MailboxGateway} that grants whatever mailbox the test scripted and remembers what was sent
 * and revoked — what the outreach screens promise is visible only in the calls they did and did not make.
 */
public class RecordingMailboxGateway implements MailboxGateway {

    /** The one signature {@link #readWebhook} accepts; anything else is refused as a forged delivery. */
    public static final String VALID_SIGNATURE = "signed-by-the-mail-service";

    private final List<SentRecord> sent = new CopyOnWriteArrayList<>();
    private final List<String> revoked = new CopyOnWriteArrayList<>();
    private final List<String> redeemedCodes = new CopyOnWriteArrayList<>();
    private volatile GrantedMailbox granted = new GrantedMailbox("grant-1", "consultant@firm.example", "google");
    private volatile RuntimeException sendFailure;
    private volatile boolean offered = true;
    private final AtomicInteger sequence = new AtomicInteger();
    private final Map<String, String> threadOfMessage = new ConcurrentHashMap<>();
    private final Map<String, List<String>> threadWriters = new ConcurrentHashMap<>();
    private volatile List<MailboxEvent> nextWebhook = List.of();

    @Override
    public boolean isOffered() {
        return offered;
    }

    @Override
    public List<String> providers() {
        return offered ? List.of("google", "microsoft") : List.of();
    }

    @Override
    public URI authorizationUri(String provider, String loginHint, String state, URI redirectUri) {
        return UriComponentsBuilder.fromUriString("https://mail.example/connect")
                .queryParam("provider", provider)
                .queryParam("redirect_uri", redirectUri.toString())
                .queryParam("state", state)
                .encode()
                .build()
                .toUri();
    }

    @Override
    public GrantedMailbox redeem(String code, URI redirectUri) {
        redeemedCodes.add(code);
        return granted;
    }

    @Override
    public SentEmail send(String grantId, OutgoingEmail email) {
        if (sendFailure != null) {
            throw sendFailure;
        }
        sent.add(new SentRecord(grantId, email));
        int number = sequence.incrementAndGet();
        String messageId = "message-" + number;
        String threadId = email.replyToMessageId() == null ? "thread-" + number
                : threadOfMessage.getOrDefault(email.replyToMessageId(), "thread-" + number);
        threadOfMessage.put(messageId, threadId);
        return new SentEmail(messageId, threadId);
    }

    @Override
    public List<MailboxEvent> readWebhook(String signature, byte[] body) {
        if (!VALID_SIGNATURE.equals(signature)) {
            throw ApiException.of(ErrorCode.MAILBOX_WEBHOOK_REJECTED);
        }
        return nextWebhook;
    }

    @Override
    public List<String> senderAddressesInThread(String grantId, String threadId, Instant since) {
        return List.copyOf(threadWriters.getOrDefault(threadId, List.of()));
    }

    /** What the next signed webhook delivery reports. */
    public void deliverNext(List<MailboxEvent> events) {
        this.nextWebhook = List.copyOf(events);
    }

    /** Someone wrote into a thread, as the reply poll will find it. */
    public void writeInto(String threadId, String fromAddress) {
        threadWriters.computeIfAbsent(threadId, key -> new CopyOnWriteArrayList<>()).add(fromAddress);
    }

    public String threadOf(String messageId) {
        return threadOfMessage.get(messageId);
    }

    @Override
    public void revoke(String grantId) {
        revoked.add(grantId);
    }

    public void grant(GrantedMailbox mailbox) {
        this.granted = mailbox;
    }

    public void failSendsWith(RuntimeException failure) {
        this.sendFailure = failure;
    }

    public void offer(boolean isOffered) {
        this.offered = isOffered;
    }

    public List<SentRecord> sent() {
        return List.copyOf(sent);
    }

    public List<String> revoked() {
        return List.copyOf(revoked);
    }

    public List<String> redeemedCodes() {
        return List.copyOf(redeemedCodes);
    }

    public void clear() {
        sent.clear();
        revoked.clear();
        redeemedCodes.clear();
        granted = new GrantedMailbox("grant-1", "consultant@firm.example", "google");
        sendFailure = null;
        offered = true;
        threadOfMessage.clear();
        threadWriters.clear();
        nextWebhook = List.of();
    }

    public record SentRecord(String grantId, OutgoingEmail email) {}

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /** {@code @Primary} so it wins over the unconfigured gateway a test profile without Nylas picks. */
        @Bean
        @Primary
        public RecordingMailboxGateway recordingMailboxGateway() {
            return new RecordingMailboxGateway();
        }
    }
}
