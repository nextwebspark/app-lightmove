package app.lightmove.api.core.email.service;
import app.lightmove.api.core.email.model.EmailMessage;

/**
 * Port for getting an email out of the building.
 *
 * <p>The reason this is an interface: nothing upstream — not signup, not invitations — should know or
 * care whether a message went to Resend, to a console, or to a test spy. That is what lets the whole
 * verification flow be built and exercised today with no provider account and no cost, and lets
 * production switch providers by changing one config key.
 *
 * <p>Implementations must not throw on a delivery failure. A signup that succeeded should not be
 * reported to the user as failed because a third-party mail API had a bad minute; the user exists,
 * and they can ask for another verification email.
 */
public interface EmailSender {

    void send(EmailMessage message);
}
