package app.lightmove.api.core.email.constant;

/**
 * Which of Uncava's sending addresses an email leaves from (#1493, B15). Privacy notices and consent requests go out
 * in bulk from their own subdomain, so their complaints and bounces never weigh on the domain that carries sign-in
 * links, invitations and password resets.
 */
public enum EmailSenderIdentity {
    ACCOUNT,
    PRIVACY
}
