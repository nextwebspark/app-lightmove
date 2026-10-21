package app.lightmove.api.outreach.service;

/**
 * The mail service would not take a file onto the email's draft, which was then deleted unsent. Nothing went, so it is
 * a definite failure rather than a wait: a retry after a throttled attach would build one more draft each minute.
 */
public class AttachmentNotAccepted extends RuntimeException {

    public AttachmentNotAccepted(Throwable cause) {
        super("a file was refused before the email was sent", cause);
    }
}
