package app.lightmove.api.core.email.constant;

/** Which of Uncava's sending addresses an email leaves from (#1493, B15): Uncava's own mail, or a firm's privacy mail. */
public enum EmailSenderIdentity {
    ACCOUNT,
    PRIVACY
}
