package app.lightmove.api.core.email.render;

import app.lightmove.api.core.email.model.EmailMessage;
import java.util.List;
import org.springframework.web.util.HtmlUtils;

/**
 * Turns an {@link EmailContent} into the two bodies an {@link EmailMessage} carries.
 *
 * <p>This is the whole of the house style: one layout, one palette, one set of type sizes, and one
 * decision per block about what it looks like. A template above it states only what the email says,
 * which is what keeps the HTML half and the plain-text half saying the same thing — they are rendered
 * from the same sentence rather than written twice.
 *
 * <p>Tables and inline styles throughout, because Outlook ignores flexbox and every mail client
 * strips a stylesheet.
 *
 * <p>Deliberately a plain class rather than a bean: it holds one derived string, carries no Spring
 * annotation that construction could render inert, and is worth being able to build with {@code new}
 * in a test.
 */
public class EmailRenderer {

    private static final String WORDMARK = "UNCAVA";

    /**
     * Versioned like {@code og-image-v2.png}, and for a sharper reason: Gmail proxies and caches every
     * remote image it fetches, so a mark redrawn in place would leave the old one in circulation.
     */
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

    /**
     * The mark beside the wordmark. Two cells with {@code valign="middle"} rather than one styled box:
     * that is the only vertical centring every mail client agrees on.
     *
     * <p>The wordmark is text, so a client that blocks images — most of them, until the reader says
     * otherwise — still shows the brand rather than a broken-image box.
     */
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

    /**
     * Fills a sentence with its phrases. The sentence is a literal from a template class; only the
     * phrases carry anything a user typed, which is why escaping can be decided here once and for all
     * rather than remembered at every call site.
     */
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
