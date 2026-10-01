package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.model.SentEmail;
import java.net.URI;
import java.util.List;

/**
 * The mail service that holds consultants' mailboxes and sends as them. One implementation per
 * service, picked by configuration; nothing outside it knows which service answered.
 *
 * <p>{@link #send} is never retried by an implementation: a request that timed out may still have
 * been delivered, and a second copy of an approach to an executive is worse than a failure.
 */
public interface MailboxGateway {

    /** False where no mail service is configured; the screens then offer nothing. */
    boolean isOffered();

    /** The mailbox hosts a consultant may connect, in the service's own names. */
    List<String> providers();

    URI authorizationUri(String provider, String loginHint, String state, URI redirectUri);

    /** Redeems the one-time code the consent screen sent back. Never retried: a code is single-use. */
    GrantedMailbox redeem(String code, URI redirectUri);

    SentEmail send(String grantId, OutgoingEmail email);

    /** Withdraws the service's access to the mailbox. */
    void revoke(String grantId);
}
