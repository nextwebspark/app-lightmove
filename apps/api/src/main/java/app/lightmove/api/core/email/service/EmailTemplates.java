package app.lightmove.api.core.email.service;
import app.lightmove.api.core.email.model.EmailMessage;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Builds the transactional emails.
 *
 * <p>Hand-built rather than templated through Thymeleaf: there are three of them, they are the only
 * ones the product needs, and a template engine would add a rendering step to debug for no gain. When
 * marketing wants control of the copy, that is the moment to introduce templates — not before.
 *
 * <p>Every interpolated value is HTML-escaped. Names and workspace names come from users, and a user
 * called {@code <script>…} must not become script in a colleague's inbox.
 */
@Component
public class EmailTemplates {

    public EmailMessage buildVerificationEmail(String recipient, String recipientName, String verifyLink) {
        String name = HtmlUtils.htmlEscape(firstName(recipientName));
        String link = HtmlUtils.htmlEscape(verifyLink);

        String html = wrap("""
                <h1 style="margin:0 0 16px;font:600 20px/1.3 -apple-system,system-ui,sans-serif;color:#1b2230">
                  Confirm your email
                </h1>
                <p style="margin:0 0 24px;font:400 14px/1.6 -apple-system,system-ui,sans-serif;color:#5a6474">
                  Hi %s — confirm this address to secure your LightMove account.
                </p>
                %s
                <p style="margin:24px 0 0;font:400 12px/1.6 -apple-system,system-ui,sans-serif;color:#98a1b3">
                  This link expires in 24 hours. If you didn't create a LightMove account, ignore this
                  email — no account will be activated.
                </p>
                """.formatted(name, button("Confirm email", link)));

        String text = """
                Confirm your email

                Hi %s — confirm this address to secure your LightMove account:

                %s

                This link expires in 24 hours. If you didn't create a LightMove account, ignore this
                email — no account will be activated.
                """.formatted(firstName(recipientName), verifyLink);

        return new EmailMessage(recipient, "Confirm your LightMove email", html, text);
    }

    public EmailMessage buildPasswordResetEmail(String recipient, String recipientName, String resetLink) {
        String name = HtmlUtils.htmlEscape(firstName(recipientName));
        String link = HtmlUtils.htmlEscape(resetLink);

        String html = wrap("""
                <h1 style="margin:0 0 16px;font:600 20px/1.3 -apple-system,system-ui,sans-serif;color:#1b2230">
                  Reset your password
                </h1>
                <p style="margin:0 0 24px;font:400 14px/1.6 -apple-system,system-ui,sans-serif;color:#5a6474">
                  Hi %s — we received a request to reset your LightMove password.
                </p>
                %s
                <p style="margin:24px 0 0;font:400 12px/1.6 -apple-system,system-ui,sans-serif;color:#98a1b3">
                  This link expires in 30 minutes and can be used once. If you didn't request this,
                  ignore this email — your password is unchanged.
                </p>
                """.formatted(name, button("Reset password", link)));

        String text = """
                Reset your password

                Hi %s — we received a request to reset your LightMove password:

                %s

                This link expires in 30 minutes and can be used once. If you didn't request this,
                ignore this email — your password is unchanged.
                """.formatted(firstName(recipientName), resetLink);

        return new EmailMessage(recipient, "Reset your LightMove password", html, text);
    }

    public EmailMessage buildInvitationEmail(String recipient, String inviterName, String workspaceName,
                                   String role, String acceptLink) {
        String inviter = HtmlUtils.htmlEscape(inviterName);
        String workspace = HtmlUtils.htmlEscape(workspaceName);
        String link = HtmlUtils.htmlEscape(acceptLink);

        String html = wrap("""
                <h1 style="margin:0 0 16px;font:600 20px/1.3 -apple-system,system-ui,sans-serif;color:#1b2230">
                  %s invited you to %s
                </h1>
                <p style="margin:0 0 24px;font:400 14px/1.6 -apple-system,system-ui,sans-serif;color:#5a6474">
                  You've been invited to join the <strong>%s</strong> workspace on LightMove as a %s.
                </p>
                %s
                <p style="margin:24px 0 0;font:400 12px/1.6 -apple-system,system-ui,sans-serif;color:#98a1b3">
                  This invitation expires in 7 days.
                </p>
                """.formatted(inviter, workspace, workspace,
                HtmlUtils.htmlEscape(role.toLowerCase()), button("Accept invitation", link)));

        String text = """
                %s invited you to %s

                You've been invited to join the %s workspace on LightMove as a %s.

                %s

                This invitation expires in 7 days.
                """.formatted(inviterName, workspaceName, workspaceName, role.toLowerCase(), acceptLink);

        return new EmailMessage(recipient, "%s invited you to %s on LightMove".formatted(inviterName, workspaceName),
                html, text);
    }

    /**
     * A client representative's portal invitation. Framed around the client they will represent, not
     * "join our workspace" — they are a guest with a read-only view of one client's mandates, not staff.
     */
    public EmailMessage buildClientInvitationEmail(String recipient, String inviterName, String workspaceName,
                                                   String clientName, String acceptLink) {
        String inviter = HtmlUtils.htmlEscape(inviterName);
        String workspace = HtmlUtils.htmlEscape(workspaceName);
        String client = HtmlUtils.htmlEscape(clientName);
        String link = HtmlUtils.htmlEscape(acceptLink);

        String html = wrap("""
                <h1 style="margin:0 0 16px;font:600 20px/1.3 -apple-system,system-ui,sans-serif;color:#1b2230">
                  %s invited you to the %s portal
                </h1>
                <p style="margin:0 0 24px;font:400 14px/1.6 -apple-system,system-ui,sans-serif;color:#5a6474">
                  %s works with <strong>%s</strong> on LightMove and has invited you to follow the searches
                  they are running for you. Set a password to open your portal.
                </p>
                %s
                <p style="margin:24px 0 0;font:400 12px/1.6 -apple-system,system-ui,sans-serif;color:#98a1b3">
                  This invitation expires in 7 days.
                </p>
                """.formatted(inviter, client, workspace, client, button("Open your portal", link)));

        String text = """
                %s invited you to the %s portal

                %s works with %s on LightMove and has invited you to follow the searches they are
                running for you. Set a password to open your portal:

                %s

                This invitation expires in 7 days.
                """.formatted(inviterName, clientName, workspaceName, clientName, acceptLink);

        return new EmailMessage(recipient,
                "%s invited you to the %s portal on LightMove".formatted(inviterName, clientName), html, text);
    }

    /**
     * Told to a colleague who is <b>already</b> a member: they've been named a representative for a
     * client. No link and no action — they already have an account and a session; this is a notice, not
     * an invitation. (An external contact who has no account gets {@link #buildInvitationEmail} instead.)
     */
    public EmailMessage buildRepresentativeAddedEmail(String recipient, String recipientName,
                                                      String adderName, String workspaceName,
                                                      String clientName) {
        String name = HtmlUtils.htmlEscape(firstName(recipientName));
        String adder = HtmlUtils.htmlEscape(adderName);
        String workspace = HtmlUtils.htmlEscape(workspaceName);
        String client = HtmlUtils.htmlEscape(clientName);

        String html = wrap("""
                <h1 style="margin:0 0 16px;font:600 20px/1.3 -apple-system,system-ui,sans-serif;color:#1b2230">
                  You now represent %s
                </h1>
                <p style="margin:0 0 8px;font:400 14px/1.6 -apple-system,system-ui,sans-serif;color:#5a6474">
                  Hi %s — %s added you as a representative for <strong>%s</strong> in the %s workspace on
                  LightMove. You'll see the mandates you're given access to next time you sign in. Nothing
                  to do — your existing login already works.
                </p>
                """.formatted(client, name, adder, client, workspace));

        String text = """
                You now represent %s

                Hi %s — %s added you as a representative for %s in the %s workspace on LightMove. You'll
                see the mandates you're given access to next time you sign in. Nothing to do — your
                existing login already works.
                """.formatted(clientName, firstName(recipientName), adderName, clientName, workspaceName);

        return new EmailMessage(recipient, "You now represent %s on LightMove".formatted(clientName),
                html, text);
    }

    /** The amber call-to-action from the mockups. Table-based because Outlook still ignores flexbox. */
    private static String button(String label, String href) {
        return """
                <table cellpadding="0" cellspacing="0" role="presentation"><tr>
                  <td style="border-radius:8px;background:#f0b429">
                    <a href="%s" style="display:inline-block;padding:11px 20px;font:600 14px -apple-system,system-ui,sans-serif;color:#141414;text-decoration:none">%s</a>
                  </td>
                </tr></table>
                """.formatted(href, label);
    }

    private static String wrap(String content) {
        return """
                <!DOCTYPE html>
                <html><body style="margin:0;padding:32px 16px;background:#f4f5f8">
                  <table cellpadding="0" cellspacing="0" role="presentation" width="100%%" style="max-width:480px;margin:0 auto">
                    <tr><td style="padding:0 0 24px">
                      <span style="display:inline-block;width:28px;height:28px;border-radius:8px;background:#f0b429;color:#141414;text-align:center;line-height:28px;font:700 13px ui-monospace,monospace">L</span>
                      <span style="margin-left:8px;font:600 15px ui-monospace,monospace;color:#1b2230">LightMove</span>
                    </td></tr>
                    <tr><td style="padding:32px;background:#ffffff;border:1px solid #e3e6ee;border-radius:14px">
                      %s
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(content);
    }

    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "there";
        }
        return fullName.trim().split("\\s+")[0];
    }
}
