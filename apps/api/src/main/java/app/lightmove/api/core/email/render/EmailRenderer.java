package app.lightmove.api.core.email.render;

import app.lightmove.api.core.email.constant.EmailCardStyle;
import app.lightmove.api.core.email.constant.EmailSenderIdentity;
import app.lightmove.api.core.email.constant.EmailTone;
import app.lightmove.api.core.email.model.EmailMessage;
import java.util.List;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders an {@link EmailContent} into HTML and plain-text bodies from one statement of the content.
 * Tables and inline styles throughout: Outlook ignores flexbox and mail clients strip stylesheets.
 */
public class EmailRenderer {

    public static final String CANDIDATE_NOTICE_LABEL = "How Uncava handles your details";
    private static final String WORDMARK = "UNCAVA";

    /** Versioned: Gmail caches every remote image, so a mark redrawn in place would stay in circulation. */
    private static final String MARK_PATH = "/brand/uncava-mark-email-v1.png";

    private final String markUrl;
    private final String siteUrl;
    private final String siteLabel;
    /** Uncava's public notice to the executives it holds (#1490); null until counsel publishes one. */
    private final String candidateNoticeUrl;

    public EmailRenderer(String baseUrl) {
        this(baseUrl, null);
    }

    public EmailRenderer(String baseUrl, String candidateNoticeUrl) {
        this.siteUrl = baseUrl;
        this.markUrl = baseUrl + MARK_PATH;
        this.siteLabel = baseUrl.replaceFirst("^https?://", "").replaceFirst("/$", "");
        this.candidateNoticeUrl = candidateNoticeUrl;
    }

    public EmailMessage render(String recipient, String subject, EmailContent content) {
        return new EmailMessage(recipient, subject, html(content, null), text(content, null));
    }

    /**
     * An email a firm sends through Uncava (a privacy notice, #923): the firm's name heads it in place of Uncava's
     * mark, the footer says Uncava carried it and links Uncava's own notice to the executive once one is published
     * (#1490), and a reply goes to the firm. It leaves from the privacy sending address (#1493).
     */
    public EmailMessage renderOnBehalfOf(String recipient, String subject, EmailContent content, String firm,
                                         String replyTo) {
        return new EmailMessage(recipient, subject, html(content, firm), text(content, firm))
                .onBehalfOf(firm, replyTo, EmailSenderIdentity.PRIVACY);
    }

    private String html(EmailContent content, String firm) {
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
                case EmailChips chips -> chipsHtml(chips);
                case EmailSectionTitle title -> sectionTitleHtml(title);
                case EmailCard card -> cardHtml(card);
            });
        }

        return page(body.toString(), firm);
    }

    private String text(EmailContent content, String firm) {
        StringBuilder text = new StringBuilder(content.heading()).append("\n\n");

        for (EmailBlock block : content.blocks()) {
            text.append(switch (block) {
                case EmailParagraph paragraph -> fill(paragraph.sentence(), paragraph.values(), false);
                case EmailAction action -> action.label() + ":\n" + action.href();
                case EmailNote note -> fill(note.sentence(), note.values(), false);
                case EmailChips chips -> chipsText(chips);
                case EmailSectionTitle title -> title.label().toUpperCase(Locale.ROOT);
                case EmailCard card -> cardText(card);
            }).append("\n\n");
        }

        String signOff = firm == null ? WORDMARK + " · " + siteUrl : "Sent by " + firm + " with Uncava";
        text.append("--\n").append(signOff).append('\n');
        if (firm != null && candidateNoticeUrl != null) {
            text.append(CANDIDATE_NOTICE_LABEL).append(": ").append(candidateNoticeUrl).append('\n');
        }
        return text.toString();
    }

    /** Two {@code valign="middle"} cells: the only vertical centring every mail client agrees on. */
    private String header(String firm) {
        if (firm != null) {
            return """
                    <span style="font:600 16px %s;color:%s">%s</span>
                    """.formatted(EmailPalette.FONT, EmailPalette.HEADING, escape(firm));
        }
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

    private String page(String content, String firm) {
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
                      %s
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(EmailPalette.PAGE, header(firm), EmailPalette.CARD, EmailPalette.BORDER,
                content, EmailPalette.FONT, EmailPalette.MUTED, footer(firm));
    }

    private String footer(String firm) {
        if (firm != null) {
            String sentBy = "Sent by " + escape(firm) + " with Uncava";
            return candidateNoticeUrl == null ? sentBy
                    : sentBy + " · <a href=\"%s\" style=\"color:%s\">%s</a>".formatted(escape(candidateNoticeUrl),
                            EmailPalette.MUTED, CANDIDATE_NOTICE_LABEL);
        }
        return "%s · <a href=\"%s\" style=\"color:%s;text-decoration:none\">%s</a>"
                .formatted(WORDMARK, siteUrl, EmailPalette.MUTED, siteLabel);
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

    /** Inline-blocks in a paragraph rather than table cells, so the counts wrap on a phone instead of squeezing. */
    private static String chipsHtml(EmailChips chips) {
        StringBuilder html = new StringBuilder("<p style=\"margin:0 0 24px\">");
        for (EmailChip chip : chips.chips()) {
            html.append("""
                    <a href="%s" style="display:inline-block;margin:0 6px 6px 0;padding:6px 12px;border-radius:8px;\
                    background:%s;font:500 13px %s;color:%s;text-decoration:none"><strong style="font-weight:600;\
                    font-size:15px">%d</strong> %s</a>""".formatted(escape(chip.href()), tintOf(chip.tone()),
                    EmailPalette.FONT, inkOf(chip.tone()), chip.count(), escape(chip.label())));
        }
        return html.append("</p>\n").toString();
    }

    private static String sectionTitleHtml(EmailSectionTitle title) {
        String aside = title.aside() == null ? "" : """
                <td align="right" style="font:400 12px %s;color:%s">%s</td>""".formatted(EmailPalette.FONT,
                EmailPalette.MUTED, escape(title.aside()));
        return """
                <table width="100%%" cellpadding="0" cellspacing="0" border="0" role="presentation" style="margin:0 0 10px"><tr>
                  <td style="font:600 12px %s;letter-spacing:0.06em;text-transform:uppercase;color:%s">%s</td>%s
                </tr></table>
                """.formatted(EmailPalette.FONT, labelOf(title.tone()), escape(title.label()), aside);
    }

    private static String cardHtml(EmailCard card) {
        boolean highlight = card.style() == EmailCardStyle.HIGHLIGHT;
        String background = switch (card.style()) {
            case HIGHLIGHT -> EmailPalette.ACCENT_TINT;
            case PLAIN -> EmailPalette.CARD;
            case QUIET -> EmailPalette.PAGE;
        };
        String rule = highlight ? EmailPalette.ACCENT_TINT_RULE : EmailPalette.BORDER;
        String secondaryInk = card.style() == EmailCardStyle.PLAIN ? EmailPalette.MUTED : EmailPalette.BODY;
        StringBuilder html = new StringBuilder("""
                <table width="100%%" cellpadding="0" cellspacing="0" border="0" role="presentation" \
                style="margin:0 0 16px;border:1px solid %s;border-radius:12px;background:%s;border-collapse:separate">
                """.formatted(EmailPalette.BORDER, background));
        boolean headed = card.title() != null;
        if (highlight && headed) {
            html.append("""
                    <tr><td style="padding:16px 16px 0;font:600 12px %s;letter-spacing:0.06em;text-transform:uppercase;\
                    color:%s">%s</td></tr>
                    """.formatted(EmailPalette.FONT, EmailPalette.ACCENT, escape(card.title())));
        } else if (headed) {
            html.append(cardHeaderHtml(card));
        }
        for (int index = 0; index < card.rows().size(); index++) {
            boolean ruled = index > 0 || (headed && !highlight);
            html.append(rowHtml(card.rows().get(index), highlight ? index + 1 : 0,
                    ruled ? "border-top:1px solid " + rule + ";" : "", secondaryInk, highlight));
        }
        if (card.footnote() != null) {
            html.append("""
                    <tr><td style="padding:10px 14px;border-top:1px solid %s;font:400 13px/1.5 %s;color:%s">%s</td></tr>
                    """.formatted(rule, EmailPalette.FONT, secondaryInk, escape(card.footnote())));
        }
        return html.append("</table>\n").toString();
    }

    private static String cardHeaderHtml(EmailCard card) {
        String subtitle = card.subtitle() == null ? "" : """
                <br><span style="font:400 13px/1.5 %s;color:%s">%s</span>""".formatted(EmailPalette.FONT,
                EmailPalette.BODY, escape(card.subtitle()));
        String link = card.link() == null ? "" : """
                <td align="right" valign="middle" style="padding-left:12px;white-space:nowrap"><a href="%s" \
                style="font:600 13px %s;color:%s;text-decoration:none">%s</a></td>""".formatted(
                escape(card.link().href()), EmailPalette.FONT, EmailPalette.ACCENT, escape(card.link().label()));
        return """
                <tr><td style="padding:12px 14px;background:%s;border-radius:12px 12px 0 0">
                  <table width="100%%" cellpadding="0" cellspacing="0" border="0" role="presentation"><tr>
                    <td valign="middle"><span style="font:600 15px/1.4 %s;color:%s">%s</span>%s</td>%s
                  </tr></table>
                </td></tr>
                """.formatted(EmailPalette.PAGE, EmailPalette.FONT, EmailPalette.HEADING, escape(card.title()),
                subtitle, link);
    }

    private static String rowHtml(EmailRow row, int number, String ruleStyle, String secondaryInk, boolean highlight) {
        String padding = highlight ? "14px 16px" : "12px 14px";
        String numberCell = number == 0 ? "" : """
                <td width="36" valign="top"><span style="display:inline-block;width:24px;height:24px;border-radius:12px;\
                background:%s;font:600 12px/24px %s;color:%s;text-align:center">%d</span></td>""".formatted(
                EmailPalette.ACCENT, EmailPalette.FONT, EmailPalette.ON_ACCENT, number);
        String leadingCell = row.leading() == null ? "" : """
                <td width="72" valign="top" style="font:600 13px/1.5 %s;color:%s">%s</td>""".formatted(
                EmailPalette.FONT, EmailPalette.ACCENT, escape(row.leading()));
        String secondary = row.secondary() == null ? "" : """
                <br><span style="font:400 13px/1.5 %s;color:%s">%s</span>""".formatted(EmailPalette.FONT,
                secondaryInk, escape(row.secondary()));
        String detail = row.detail() == null ? "" : """
                <br><span style="font:400 13px/1.5 %s;color:%s">%s</span>""".formatted(EmailPalette.FONT,
                EmailPalette.BODY, escape(row.detail()));
        String badge = row.badge() == null ? "" : """
                <td align="right" valign="top" style="padding-left:12px;white-space:nowrap"><span style="display:inline-block;\
                padding:3px 8px;border-radius:8px;background:%s;font:600 12px %s;color:%s">%s</span></td>""".formatted(
                tintOf(row.badgeTone()), EmailPalette.FONT, inkOf(row.badgeTone()), escape(row.badge()));
        return """
                <tr><td style="padding:%s;%s">
                  <table width="100%%" cellpadding="0" cellspacing="0" border="0" role="presentation"><tr>%s%s
                    <td valign="top"><a href="%s" style="font:600 14px/1.5 %s;color:%s;text-decoration:none">%s</a>%s%s</td>%s
                  </tr></table>
                </td></tr>
                """.formatted(padding, ruleStyle, numberCell, leadingCell, escape(row.href()), EmailPalette.FONT,
                EmailPalette.HEADING, escape(row.primary()), secondary, detail, badge);
    }

    private static String chipsText(EmailChips chips) {
        return String.join(" · ", chips.chips().stream().map(chip -> chip.count() + " " + chip.label()).toList());
    }

    private static String cardText(EmailCard card) {
        StringBuilder text = new StringBuilder();
        if (card.title() != null) {
            text.append(card.style() == EmailCardStyle.HIGHLIGHT
                    ? card.title().toUpperCase(Locale.ROOT) : card.title());
            if (card.subtitle() != null) {
                text.append(" — ").append(card.subtitle());
            }
            text.append('\n');
        }
        for (int index = 0; index < card.rows().size(); index++) {
            EmailRow row = card.rows().get(index);
            text.append(card.style() == EmailCardStyle.HIGHLIGHT ? (index + 1) + ". " : "- ");
            if (row.leading() != null) {
                text.append(row.leading()).append("  ");
            }
            text.append(row.primary());
            if (row.secondary() != null) {
                text.append(" — ").append(row.secondary());
            }
            if (row.badge() != null) {
                text.append(" (").append(row.badge()).append(')');
            }
            if (row.detail() != null) {
                text.append("\n  ").append(row.detail());
            }
            text.append("\n  ").append(row.href()).append('\n');
        }
        if (card.footnote() != null) {
            text.append(card.footnote()).append('\n');
        }
        if (card.link() != null) {
            text.append(card.link().label()).append(": ").append(card.link().href()).append('\n');
        }
        return text.toString().stripTrailing();
    }

    private static String tintOf(EmailTone tone) {
        return switch (tone) {
            case ACCENT -> EmailPalette.ACCENT_TINT;
            case SIGNAL -> EmailPalette.SIGNAL_TINT;
            case NEUTRAL -> EmailPalette.NEUTRAL_TINT;
        };
    }

    private static String inkOf(EmailTone tone) {
        return switch (tone) {
            case ACCENT -> EmailPalette.ACCENT;
            case SIGNAL -> EmailPalette.SIGNAL;
            case NEUTRAL -> EmailPalette.NEUTRAL_TEXT;
        };
    }

    private static String labelOf(EmailTone tone) {
        return tone == EmailTone.NEUTRAL ? EmailPalette.LABEL : inkOf(tone);
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
