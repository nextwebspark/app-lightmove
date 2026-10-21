package app.lightmove.api.outreach.model;

import java.time.Instant;

/**
 * One message someone wrote into a thread we started, as the mailbox holds it. {@code text} is its words, already
 * plain text and read only where the mailbox lets us; null otherwise. {@link #toString()} leaves the words out.
 */
public record ThreadMessage(String providerMessageId, String fromAddress, Instant receivedAt, String text) {

    @Override
    public String toString() {
        return "ThreadMessage[providerMessageId=" + providerMessageId + ", fromAddress=" + fromAddress
                + ", receivedAt=" + receivedAt + ", text=" + (text == null ? "null" : "<withheld>") + "]";
    }
}
