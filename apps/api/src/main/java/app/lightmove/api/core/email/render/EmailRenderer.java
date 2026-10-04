package app.lightmove.api.core.email.render;

import app.lightmove.api.core.email.model.EmailMessage;
import java.util.List;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders an {@link EmailContent} into HTML and plain-text bodies from one statement of the content.
 * Tables and inline styles throughout: Outlook ignores flexbox and mail clients strip stylesheets.
 */
public class EmailRenderer {

    private static final String WORDMARK = "UNCAVA";

    /** Versioned: Gmail caches every remote image, so a mark redrawn in place would stay in circulation. */
    private static final String MARK_PATH = "/brand/uncava-mark-email-v1.png";

    private final String markUrl;
    private final String siteUrl;
    private final String siteLabel;

    public EmailRenderer(String baseUrl) {
        this.siteUrl = baseUrl;
        this.markUrl = baseUrl + MARK_PATH;
        this.siteLabel = baseUrl.replaceFirst("^https?://", "").replaceFirst("/$", "");
    }

    public EmailMessage render(String recipient, String subject, EmailContent content) {
        return new EmailMessage(recipient, subject, html(content), text(content));
    }

    private String html(EmailContent content) {
        StringBuilder body = new StringBuilder("""
                <h1 style="margin:0 0 16px;font:600 20px/1.3 %s;color:%s">%s</h1>
                """.formatted(EmailPalette.FONT, EmailPalette.HEADING, escape(content.heading())));

        for (EmailBlock block : content.blocks()) {
            body.append(switch (block) {
                case EmailParagraph paragraph -> """
                        <p style="margin:0 0 24px;font:400 14px/1.6 %s;color:%s">%s</p>
                        """.formatted(EmailPalette.FONT, EmailPalette.BODY,
                        fill(paragraph.sentence(), paragraph.values(), true));
                case EmailAction action -> button(action);
                case EmailNote note -> """
                        <p style="margin:24px 0 0;font:400 12px/1.6 %s;color:%s">%s</p>
                        """.formatted(EmailPalette.FONT, EmailPalette.MUTED,
                        fill(note.sentence(), note.values(), true));
            });
        }

        return page(body.toString());
    }

    private String text(EmailContent content) {
        StringBuilder text = new StringBuilder(content.heading()).append("\n\n");

        for (EmailBlock block : content.blocks()) {
            text.append(switch (block) {
                case EmailParagraph paragraph -> fill(paragraph.sentence(), paragraph.values(), false);
                case EmailAction action -> action.label() + ":\n" + action.href();
                case EmailNote note -> fill(note.sentence(), note.values(), false);
            }).append("\n\n");
        }

        return text.append("--\n").append(WORDMARK).append(" · ").append(siteUrl).append('\n').toString();
    }

    /** Two {@code valign="middle"} cells: the only vertical centring every mail client agrees on. */
    private String header() {
        return """
                <table cellpadding="0" cellspacing="0" border="0" role="presentation"><tr>
                  <td valign="middle"><img src="%s" width="29" height="42" alt="Uncava" style="display:block;border:0"></td>
                  <td width="14"></td>
                  <td valign="middle"><span style="font:400 15px %s;letter-spacing:0.32em;color:%s">%s</span></td>
                </tr></table>
                """.formatted(markUrl, EmailPalette.FONT, EmailPalette.HEADING, WORDMARK);
    }

    private String button(EmailAction action) {
        return """
                <table cellpadding="0" cellspacing="0" border="0" role="presentation"><tr>
                  <td style="border-radius:8px;background:%s">
                    <a href="%s" style="display:inline-block;padding:11px 20px;font:600 14px %s;color:%s;text-decoration:none">%s</a>
                  </td>
                </tr></table>
                """.formatted(EmailPalette.ACCENT, escape(action.href()), EmailPalette.FONT,
                EmailPalette.ON_ACCENT, escape(action.label()));
    }

    private String page(String content) {
        return """
                <!DOCTYPE html>
                <html><body style="margin:0;padding:32px 16px;background:%s">
                  <table cellpadding="0" cellspacing="0" role="presentation" width="100%%" style="max-width:480px;margin:0 auto">
                    <tr><td style="padding:0 0 24px">
                      %s
                    </td></tr>
                    <tr><td style="padding:32px;background:%s;border:1px solid %s;border-radius:14px">
                      %s
                    </td></tr>
                    <tr><td style="padding:20px 4px 0;font:400 12px/1.6 %s;color:%s">
                      %s · <a href="%s" style="color:%s;text-decoration:none">%s</a>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(EmailPalette.PAGE, header(), EmailPalette.CARD, EmailPalette.BORDER,
                content, EmailPalette.FONT, EmailPalette.MUTED, WORDMARK, siteUrl,
                EmailPalette.MUTED, siteLabel);
    }

    /** The sentence is a template literal; only the phrases carry user input, so escaping is decided here once. */
    private static String fill(String sentence, List<EmailPhrase> values, boolean asHtml) {
        Object[] rendered = values.stream()
                .map(phrase -> asHtml ? renderHtml(phrase) : phrase.text())
                .toArray();
        return sentence.formatted(rendered);
    }

    private static String renderHtml(EmailPhrase phrase) {
        String escaped = escape(phrase.text());
        return phrase.emphasised() ? "<strong>" + escaped + "</strong>" : escaped;
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
