package app.lightmove.api.outreach.model;

/** What a completed mailbox sign-in hands back: the mail service's handle for it, and whose it is. */
public record GrantedMailbox(String grantId, String address, String provider) {}
