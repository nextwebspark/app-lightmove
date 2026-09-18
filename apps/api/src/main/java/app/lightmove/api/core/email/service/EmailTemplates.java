package app.lightmove.api.core.email.service;

import static app.lightmove.api.core.email.render.EmailPhrase.plain;
import static app.lightmove.api.core.email.render.EmailPhrase.strong;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.model.EmailMessage;
import app.lightmove.api.core.email.render.EmailAction;
import app.lightmove.api.core.email.render.EmailContent;
import app.lightmove.api.core.email.render.EmailNote;
import app.lightmove.api.core.email.render.EmailParagraph;
import app.lightmove.api.core.email.render.EmailRenderer;
import org.springframework.stereotype.Component;

/**
 * What each transactional email says. How it looks is {@link EmailRenderer}'s, and nothing here knows
 * a colour or a tag: a template names a subject, a heading and its blocks, and the renderer produces
 * the HTML and the plain text from that one statement of the content.
 *
 * <p>Values a user typed travel as {@code EmailPhrase}, which is what escapes them for the HTML half
 * and leaves them intact for the text half. A workspace called {@code <script>…} is a name here and
 * text in a colleague's inbox.
 */
@Component
public class EmailTemplates {

    private final EmailRenderer renderer;

    public EmailTemplates(LightMoveProperties properties) {
        this.renderer = new EmailRenderer(properties.web().baseUrl());
    }

    public EmailMessage buildVerificationEmail(String recipient, String recipientName, String verifyLink) {
        return renderer.render(recipient, "Confirm your Uncava email", EmailContent.of(
                "Confirm your email",
                EmailParagraph.of("Hi %s — confirm this address to carry on setting up your Uncava "
                                + "account. You will be signed in and taken straight to the next step.",
                        plain(firstName(recipientName))),
                new EmailAction("Confirm email", verifyLink),
                EmailNote.of("This link expires in 24 hours. If you didn't create a Uncava account, "
                        + "ignore this email — no account will be activated.")));
    }

    public EmailMessage buildPasswordResetEmail(String recipient, String recipientName, String resetLink) {
        return renderer.render(recipient, "Reset your Uncava password", EmailContent.of(
                "Reset your password",
                EmailParagraph.of("Hi %s — we received a request to reset your Uncava password.",
                        plain(firstName(recipientName))),
                new EmailAction("Reset password", resetLink),
                EmailNote.of("This link expires in 30 minutes and can be used once. If you didn't "
                        + "request this, ignore this email — your password is unchanged.")));
    }

    /**
     * Tells someone their account is locked, because the login response deliberately will not.
     *
     * <p>Login answers the same {@code INVALID_CREDENTIALS} for a locked account as for an unknown
     * address, which leaves the real owner with a correct password that keeps failing and no
     * explanation. This is the explanation, sent where only they can read it. No link, no token.
     */
    public EmailMessage buildAccountLockedEmail(String recipient, String recipientName, String lockedUntil) {
        return renderer.render(recipient, "Your Uncava account is temporarily locked", EmailContent.of(
                "Your account is temporarily locked",
                EmailParagraph.of("Hi %s — too many sign-in attempts failed, so we locked your Uncava "
                                + "account until %s. Signing in before then will be refused even with "
                                + "the right password.",
                        plain(firstName(recipientName)), strong(lockedUntil)),
                EmailNote.of("Resetting your password lifts the lock immediately. If none of these "
                        + "attempts were yours, reset it anyway — somebody knows your address and is "
                        + "guessing.")));
    }

    /**
     * Confirms a password change to the mailbox, which is the one channel an attacker who changed it
     * does not control. The reset link is the recovery route if the change was not theirs.
     */
    public EmailMessage buildPasswordChangedEmail(String recipient, String recipientName, String resetLink) {
        return renderer.render(recipient, "Your Uncava password was changed", EmailContent.of(
                "Your password was changed",
                EmailParagraph.of("Hi %s — your Uncava password was just changed, and every other "
                        + "signed-in device was signed out.", plain(firstName(recipientName))),
                new EmailAction("Reset your password", resetLink),
                EmailNote.of("If this was you, nothing more to do. If it was not, reset your password "
                        + "now and tell your workspace admin.")));
    }

    public EmailMessage buildInvitationEmail(String recipient, String inviterName, String workspaceName,
                                             String role, String acceptLink) {
        return renderer.render(recipient,
                "%s invited you to %s on Uncava".formatted(inviterName, workspaceName),
                EmailContent.of(
                        "%s invited you to %s".formatted(inviterName, workspaceName),
                        EmailParagraph.of("You've been invited to join the %s workspace on Uncava as a %s.",
                                strong(workspaceName), plain(role.toLowerCase())),
                        new EmailAction("Accept invitation", acceptLink),
                        EmailNote.of("This invitation expires in 7 days.")));
    }

    /**
     * A client representative's portal invitation. Framed around the client they will represent, not
     * "join our workspace" — they are a guest with a read-only view of one client's mandates, not staff.
     */
    public EmailMessage buildClientInvitationEmail(String recipient, String inviterName, String workspaceName,
                                                   String clientName, String acceptLink) {
        return renderer.render(recipient,
                "%s invited you to the %s portal on Uncava".formatted(inviterName, clientName),
                EmailContent.of(
                        "%s invited you to the %s portal".formatted(inviterName, clientName),
                        EmailParagraph.of("%s works with %s on Uncava and has invited you to follow the "
                                        + "searches they are running for you. Set a password to open "
                                        + "your portal.",
                                plain(workspaceName), strong(clientName)),
                        new EmailAction("Open your portal", acceptLink),
                        EmailNote.of("This invitation expires in 7 days.")));
    }

    /**
     * Told to a colleague who is <b>already</b> a member. A notice, not an invitation: an external
     * contact with no account gets {@link #buildInvitationEmail} instead.
     */
    public EmailMessage buildRepresentativeAddedEmail(String recipient, String recipientName,
                                                      String adderName, String workspaceName,
                                                      String clientName) {
        return renderer.render(recipient, "You now represent %s on Uncava".formatted(clientName),
                EmailContent.of(
                        "You now represent %s".formatted(clientName),
                        EmailParagraph.of("Hi %s — %s added you as a representative for %s in the %s "
                                        + "workspace on Uncava. You'll see the mandates you're given "
                                        + "access to next time you sign in. Nothing to do — your "
                                        + "existing login already works.",
                                plain(firstName(recipientName)), plain(adderName), strong(clientName),
                                plain(workspaceName))));
    }

    /**
     * Told to an <b>active</b> representative when a mandate is shared with them. An INVITED one gets
     * nothing here: accepting the portal invitation already in their inbox seats them on every mandate
     * parked for them.
     */
    public EmailMessage buildAttachedToMandateEmail(String recipient, String recipientName,
                                                    String adderName, String clientName,
                                                    String positionTitle) {
        return renderer.render(recipient,
                "The %s search was shared with you on Uncava".formatted(positionTitle),
                EmailContent.of(
                        "A search was shared with you",
                        EmailParagraph.of("Hi %s — %s gave you access to the %s search for %s on Uncava. "
                                        + "You'll find it in your portal next time you sign in. Nothing "
                                        + "to do — your existing login already works.",
                                plain(firstName(recipientName)), plain(adderName), strong(positionTitle),
                                strong(clientName))));
    }

    /**
     * Told to a staff member seated on a mandate. The workspace invitation says someone may work here;
     * this says which search is theirs, which is the thing that actually hands them work.
     */
    public EmailMessage buildAddedToProjectEmail(String recipient, String recipientName, String adderName,
                                                 String positionTitle, String clientName, String role,
                                                 String projectLink) {
        return renderer.render(recipient,
                "You were added to the %s search on Uncava".formatted(positionTitle),
                EmailContent.of(
                        "You were added to a search",
                        EmailParagraph.of("Hi %s — %s added you to the %s search for %s as a %s.",
                                plain(firstName(recipientName)), plain(adderName), strong(positionTitle),
                                strong(clientName), plain(role.toLowerCase())),
                        new EmailAction("Open the search", projectLink),
                        EmailNote.of("You'll also find it under your projects next time you sign in.")));
    }

    /** Told to a staff member whose seat changed hands — a lead handing over, a researcher promoted. */
    public EmailMessage buildProjectRoleChangedEmail(String recipient, String recipientName, String actorName,
                                                     String positionTitle, String clientName, String role,
                                                     String projectLink) {
        return renderer.render(recipient,
                "Your role on the %s search changed".formatted(positionTitle),
                EmailContent.of(
                        "Your role on a search changed",
                        EmailParagraph.of("Hi %s — %s changed your role on the %s search for %s. "
                                        + "You are now a %s on it.",
                                plain(firstName(recipientName)), plain(actorName), strong(positionTitle),
                                strong(clientName), strong(role.toLowerCase())),
                        new EmailAction("Open the search", projectLink)));
    }

    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "there";
        }
        return fullName.trim().split("\\s+")[0];
    }
}
