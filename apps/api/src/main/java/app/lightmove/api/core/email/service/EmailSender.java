package app.lightmove.api.core.email.service;
import app.lightmove.api.core.email.model.EmailMessage;

/**
 * Port for sending email, so nothing upstream knows the provider. Implementations must not throw on
 * a delivery failure: a signup that succeeded must not be reported as failed.
 */
public interface EmailSender {

    void send(EmailMessage message);

    /** As {@link #send}, also answering whether the provider accepted it, for a caller that tells someone. */
    default boolean trySend(EmailMessage message) {
        send(message);
        return true;
    }
}
