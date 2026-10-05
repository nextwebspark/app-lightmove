package app.lightmove.api.outreach.constant;

import app.lightmove.api.outreach.model.MailboxGrants;

/**
 * Which gateway made a mailbox connection, and so answers every call for it: Nylas's hosted grant, or our own
 * gateway holding the provider's refresh token.
 */
public enum MailboxGatewayKind {
    NYLAS,
    DIRECT;

    /** Read off the grant id itself, so a call for a connection already deleted still reaches its own gateway. */
    public static MailboxGatewayKind ofGrant(String grantId) {
        return MailboxGrants.isDirect(grantId) ? DIRECT : NYLAS;
    }
}
