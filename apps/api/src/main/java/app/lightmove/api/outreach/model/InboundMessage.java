package app.lightmove.api.outreach.model;

import java.time.Instant;

/**
 * A message arrived in a thread of a connected mailbox. {@code html} is its body where the service sent one, which
 * is read only for a thread Uncava started and a mailbox that lets us; the id and time may be missing.
 * {@link #toString()} leaves the body out.
 */
public record InboundMessage(String grantId, String threadId, String fromAddress, String messageId,
                             Instant receivedAt, String html) implements MailboxEvent {

    public InboundMessage(String grantId, String threadId, String fromAddress) {
        this(grantId, threadId, fromAddress, null, null, null);
    }

    @Override
    public String toString() {
        return "InboundMessage[grantId=" + grantId + ", threadId=" + threadId + ", fromAddress=" + fromAddress
                + ", messageId=" + messageId + ", receivedAt=" + receivedAt
                + ", html=" + (html == null ? "null" : "<withheld>") + "]";
    }
}
