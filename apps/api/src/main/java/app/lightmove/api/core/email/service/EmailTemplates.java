package app.lightmove.api.core.email.service;

import static app.lightmove.api.core.email.render.EmailPhrase.plain;
import static app.lightmove.api.core.email.render.EmailPhrase.strong;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.model.EmailMessage;
import app.lightmove.api.core.email.render.EmailAction;
import app.lightmove.api.core.email.render.EmailBlock;
import app.lightmove.api.core.email.render.EmailContent;
import app.lightmove.api.core.email.render.EmailNote;
import app.lightmove.api.core.email.render.EmailParagraph;
import app.lightmove.api.core.email.render.EmailRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * What each transactional email says; how it looks is {@link EmailRenderer}'s. User-typed values travel
 * as {@code EmailPhrase}, which escapes them for the HTML half.
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

    /** The only place a lockout is explained: login deliberately will not. No link, no token. */
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

    /** To the mailbox, the one channel an attacker who changed the password does not control. */
    public EmailMessage buildPasswordChangedEmail(String recipient, String recipientName, String resetLink) {
        return renderer.render(recipient, "Your Uncava password was changed", EmailContent.of(
                "Your password was changed",
                EmailParagraph.of("Hi %s — your Uncava password was just changed, and every other "
                        + "signed-in device was signed out.", plain(firstName(recipientName))),
                new EmailAction("Reset your password", resetLink),
                EmailNote.of("If this was you, nothing more to do. If it was not, reset your password "
                        + "now and tell your workspace admin.")));
    }

    /** To the mailbox, as a password change is: a stolen session that turned it on would lock the owner out. */
    public EmailMessage buildTwoFactorEnabledEmail(String recipient, String recipientName, String securityLink) {
        return renderer.render(recipient, "Two-factor authentication is on for your Uncava account", EmailContent.of(
                "Two-factor authentication is on",
                EmailParagraph.of("Hi %s — signing in to Uncava now asks for a code from your authenticator "
                        + "app, and every other signed-in device was signed out.", plain(firstName(recipientName))),
                new EmailAction("Review your security settings", securityLink),
                EmailNote.of("If this was you, keep your recovery codes somewhere safe. If it was not, reset "
                        + "your password now and tell your workspace admin.")));
    }

    public EmailMessage buildTwoFactorDisabledEmail(String recipient, String recipientName, String resetLink) {
        return renderer.render(recipient, "Two-factor authentication is off for your Uncava account", EmailContent.of(
                "Two-factor authentication is off",
                EmailParagraph.of("Hi %s — signing in to Uncava no longer asks for a code, and every other "
                        + "signed-in device was signed out.", plain(firstName(recipientName))),
                new EmailAction("Reset your password", resetLink),
                EmailNote.of("If this was you, nothing more to do. If it was not, reset your password now "
                        + "and tell your workspace admin.")));
    }

    public EmailMessage buildTwoFactorResetEmail(String recipient, String recipientName, String workspaceName,
                                                 String signInLink) {
        return renderer.render(recipient, "Your Uncava two-factor authentication was reset", EmailContent.of(
                "Two-factor authentication was reset",
                EmailParagraph.of("Hi %s — an admin of %s reset your two-factor authentication, and every "
                        + "signed-in device was signed out. Signing in no longer asks for a code until you set it up "
                        + "again.", plain(firstName(recipientName)), plain(workspaceName)),
                new EmailAction("Sign in", signInLink),
                EmailNote.of("If you did not ask for this, reset your password now and tell your workspace "
                        + "admin.")));
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

    /** Framed around the client they represent: a read-only guest, not staff. */
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

    /** A notice to an existing member; an external contact gets {@link #buildInvitationEmail} instead. */
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

    /** Active representatives only: accepting an invitation already seats an INVITED one on every parked mandate. */
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

    /** To a workspace's admins, ahead of its own OAuth app's client secret lapsing and on the day it does. */
    public EmailMessage buildIntegrationSecretExpiryEmail(String recipient, String recipientName, String workspaceName,
                                                          String providerName, String expiresOn, long daysLeft,
                                                          String settingsLink) {
        boolean expired = daysLeft < 0;
        String subject = expired
                ? "Your %s app's client secret has expired".formatted(providerName)
                : daysLeft == 0
                        ? "Your %s app's client secret expires today".formatted(providerName)
                        : "Your %s app's client secret expires on %s".formatted(providerName, expiresOn);
        EmailParagraph lead = expired
                ? EmailParagraph.of("Hi %s — the client secret of %s's own %s app expired on %s. Mail, calendars "
                                + "and links that go through it stop working until a new secret is saved.",
                        plain(firstName(recipientName)), strong(workspaceName), plain(providerName), strong(expiresOn))
                : daysLeft == 0
                ? EmailParagraph.of("Hi %s — the client secret of %s's own %s app expires today, %s. When it "
                                + "does, mail, calendars and links that go through it stop working until a new "
                                + "secret is saved.",
                        plain(firstName(recipientName)), strong(workspaceName), plain(providerName), strong(expiresOn))
                : EmailParagraph.of("Hi %s — the client secret of %s's own %s app expires on %s, in %s %s. "
                                + "When it does, mail, calendars and links that go through it stop working until "
                                + "a new secret is saved.",
                        plain(firstName(recipientName)), strong(workspaceName), plain(providerName), strong(expiresOn),
                        plain(String.valueOf(daysLeft)), plain(daysLeft == 1 ? "day" : "days"));
        return renderer.render(recipient, subject, EmailContent.of(
                expired ? "A client secret has expired" : daysLeft == 0 ? "A client secret expires today"
                        : "A client secret expires soon",
                lead,
                new EmailAction("Open Integrations", settingsLink),
                EmailNote.of("Create a new secret in the provider's console, then paste it and its new expiry "
                        + "date into Settings → Integrations. You get this because you manage the workspace.")));
    }

    /** To a workspace's billing managers, once per billing month at 80% and again at 90% of its plan's credits. */
    public EmailMessage buildContactCreditsLowEmail(String recipient, String recipientName, String workspaceName,
                                                    int usedPercent, long creditsLeft, String resetsOn,
                                                    EmailAction moreCredits) {
        boolean urgent = usedPercent >= 90;
        return renderer.render(recipient,
                "%d%% of this month's contact credits used".formatted(usedPercent), EmailContent.of(
                        urgent ? "Contact credits are nearly gone" : "Contact credits are running low",
                        EmailParagraph.of("Hi %s — %s has used %s of this month's contact credits: %s left until "
                                        + "they reset on %s.%s",
                                plain(firstName(recipientName)), strong(workspaceName),
                                strong(usedPercent + "%"), strong(creditsLeftOf(creditsLeft)), strong(resetsOn),
                                plain(urgent ? " Once they are gone, Find email and Find phone stop until then." : "")),
                        moreCredits,
                        EmailNote.of("You get this because you manage billing for the workspace.")));
    }

    public EmailMessage buildContactCreditsUsedUpEmail(String recipient, String recipientName, String workspaceName,
                                                       String resetsOn, EmailAction moreCredits) {
        return renderer.render(recipient, "Contact credits used up", EmailContent.of(
                "Contact credits used up",
                EmailParagraph.of("Hi %s — %s has no contact credits left, so Find email and Find phone are "
                                + "paused until the credits reset on %s, or until more are added.",
                        plain(firstName(recipientName)), strong(workspaceName), strong(resetsOn)),
                moreCredits,
                EmailNote.of("You get this because you manage billing for the workspace.")));
    }

    /** Once per failed invoice: Stripe retries the card on its own, and each attempt is another event. */
    public EmailMessage buildPaymentFailedEmail(String recipient, String recipientName, String workspaceName,
                                                long graceDays, String billingLink) {
        return renderer.render(recipient, "Your Uncava payment failed", EmailContent.of(
                "A payment failed",
                EmailParagraph.of("Hi %s — the latest payment for %s's Uncava subscription did not go through. "
                                + "Update the card from Settings → Billing and the invoice is retried.",
                        plain(firstName(recipientName)), strong(workspaceName)),
                EmailParagraph.of("If it is still unpaid after %s %s, no new month of contact credits is granted "
                                + "until it is.",
                        plain(String.valueOf(graceDays)), plain(graceDays == 1 ? "day" : "days")),
                new EmailAction("Open billing", billingLink),
                EmailNote.of("You get this because you manage billing for the workspace.")));
    }

    /** Once per pack bought, a week before what is left of it lapses. */
    public EmailMessage buildPurchasedCreditsExpiringEmail(String recipient, String recipientName,
                                                           String workspaceName, long credits, String expiresOn,
                                                           String billingLink) {
        return renderer.render(recipient, "Bought contact credits expire on %s".formatted(expiresOn),
                EmailContent.of(
                        "Bought credits expire soon",
                        EmailParagraph.of("Hi %s — %s of the contact credits %s bought expire on %s. Bought credits "
                                        + "are spent after the month's plan credits, so they lapse unless lookups "
                                        + "run past the plan's before then.",
                                plain(firstName(recipientName)), strong(creditsLeftOf(credits)),
                                strong(workspaceName), strong(expiresOn)),
                        new EmailAction("Open billing", billingLink),
                        EmailNote.of("You get this because you manage billing for the workspace.")));
    }

    /** Once per trial, a few days before it ends. */
    public EmailMessage buildTrialEndingEmail(String recipient, String recipientName, String workspaceName,
                                              String endsOn, String billingLink) {
        return renderer.render(recipient, "Your Uncava trial ends on %s".formatted(endsOn), EmailContent.of(
                "Your trial ends soon",
                EmailParagraph.of("Hi %s — %s's Uncava trial ends on %s. Choose a plan before then and your team "
                                + "keeps finding contacts, searching and using AI without a break.",
                        plain(firstName(recipientName)), strong(workspaceName), strong(endsOn)),
                new EmailAction("Choose a plan", billingLink),
                EmailNote.of("You get this because you manage billing for the workspace.")));
    }

    /** Once per trial, the day it ends unpaid. */
    public EmailMessage buildTrialEndedEmail(String recipient, String recipientName, String workspaceName,
                                             String billingLink) {
        return renderer.render(recipient, "Your Uncava trial has ended", EmailContent.of(
                "Your trial has ended",
                EmailParagraph.of("Hi %s — %s's Uncava trial has ended. Everything your team mapped is still there; "
                                + "finding contacts, searching and AI resume once a plan is chosen.",
                        plain(firstName(recipientName)), strong(workspaceName)),
                new EmailAction("Choose a plan", billingLink),
                EmailNote.of("You get this because you manage billing for the workspace.")));
    }

    /** A task someone else put on the recipient, still unread in the bell after the email delay. */
    public EmailMessage buildTaskAssignedEmail(String recipient, String recipientName, String actorName,
                                               String taskTitle, String executiveName, String positionTitle,
                                               String due, String link) {
        return renderer.render(recipient, "%s assigned you: %s".formatted(actorName, taskTitle), EmailContent.of(
                "A task for you",
                EmailParagraph.of("Hi %s — %s assigned you %s on %s for the %s search, due %s.",
                        plain(firstName(recipientName)), plain(actorName), strong(taskTitle), strong(executiveName),
                        plain(positionTitle), strong(due)),
                new EmailAction("Open the task", link),
                EmailNote.of("You get this because it was still unread in Uncava. Change what Uncava emails you "
                        + "in Settings → Notifications.")));
    }

    /** A mention in a note, still unread after the email delay. Names the note and never quotes it. */
    public EmailMessage buildMentionedEmail(String recipient, String recipientName, String actorName,
                                            String executiveName, String positionTitle, String link) {
        return renderer.render(recipient, "%s mentioned you in a note".formatted(actorName), EmailContent.of(
                "You were mentioned",
                EmailParagraph.of("Hi %s — %s mentioned you in a note on %s for the %s search.",
                        plain(firstName(recipientName)), plain(actorName), strong(executiveName),
                        plain(positionTitle)),
                new EmailAction("Read the note", link),
                EmailNote.of("Notes stay in Uncava, so this email doesn't carry it. Change what Uncava emails you "
                        + "in Settings → Notifications.")));
    }

    /** The morning's summary: what is due today, what is overdue, and mentions still unread. */
    public EmailMessage buildNotificationDigestEmail(String recipient, String recipientName, String date,
                                                     int dueToday, int overdue, int unreadMentions,
                                                     List<String> lines, String link) {
        List<EmailBlock> blocks = new ArrayList<>();
        blocks.add(EmailParagraph.of("Hi %s — here is your day: %s due today, %s overdue and %s unread.",
                plain(firstName(recipientName)), strong(countOf(dueToday, "task")), strong(countOf(overdue, "task")),
                strong(countOf(unreadMentions, "mention"))));
        lines.forEach(line -> blocks.add(EmailParagraph.of("%s", plain(line))));
        blocks.add(new EmailAction("Open My tasks", link));
        blocks.add(EmailNote.of("A digest goes each morning there is something on your list. Turn it off in "
                + "Settings → Notifications."));
        return renderer.render(recipient, "Your Uncava day — %s".formatted(date),
                new EmailContent("Your day in Uncava", blocks));
    }

    private static String countOf(int count, String noun) {
        return count == 1 ? "1 " + noun : count + " " + noun + "s";
    }

    private static String creditsLeftOf(long credits) {
        return credits == 1 ? "1 credit" : String.format(Locale.ENGLISH, "%,d credits", credits);
    }

    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "there";
        }
        return fullName.trim().split("\\s+")[0];
    }
}
