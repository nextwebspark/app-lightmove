package app.lightmove.api.core.email.service;
import app.lightmove.api.core.email.model.EmailMessage;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Builds the transactional emails.
 *
 * <p>Hand-built rather than templated through Thymeleaf: there are two of them, they are the only two
 * this session needs, and a template engine would add a rendering step to debug for no gain. When
 * marketing wants control of the copy, that is the moment to introduce templates — not before.
 *
 * <p>Every interpolated value is HTML-escaped. Names and workspace names come from users, and a user
 * called {@code <script>…} must not become script in a colleague's inbox.
 */
@Component
public class EmailTemplates {

    public EmailMessage verifyEmail(String recipient, String recipientName, String verifyLink) {
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

    public EmailMessage invitation(String recipient, String inviterName, String workspaceName,
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
