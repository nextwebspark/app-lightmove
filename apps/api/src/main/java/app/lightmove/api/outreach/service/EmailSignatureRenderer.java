package app.lightmove.api.outreach.service;

import app.lightmove.api.core.storage.constant.PhotoShape;
import app.lightmove.api.core.storage.service.EmailImageService;
import app.lightmove.api.outreach.constant.SignatureLayout;
import app.lightmove.api.outreach.model.OutreachMarkdownEmail;
import app.lightmove.api.outreach.model.RenderedSignature;
import app.lightmove.api.outreach.model.SignatureContent;
import app.lightmove.api.outreach.model.SignatureFields;
import app.lightmove.api.outreach.model.SignatureLogo;
import app.lightmove.api.outreach.model.SignaturePicture;
import app.lightmove.api.outreach.model.SignatureSender;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import org.springframework.web.util.HtmlUtils;

/**
 * Draws a signature the way mail clients agree on: nested tables with inline styles, a fixed size on every picture, no
 * class, no background image. Every value is escaped, a link goes only to http, https, mailto or tel, and the only
 * pictures are ones Uncava serves: the photo, the logo, and the signer's own uploaded pictures. Text takes no
 * colour of its own but the muted lines', so a client's dark mode can turn it light.
 */
public final class EmailSignatureRenderer {

    private static final String FONT = "font-family:Arial,Helvetica,sans-serif;font-size:13px;line-height:1.45";
    private static final String MUTED = "color:#6b7280";
    private static final String LINK = "color:#1a56db;text-decoration:underline";
    private static final int PHOTO_PX = 64;
    private static final int LOGO_MAX_WIDTH_PX = 200;
    private static final int LOGO_MAX_HEIGHT_PX = 48;
    private static final int FOOTER_PICTURE_MAX_WIDTH_PX = 160;
    private static final int FOOTER_PICTURE_MAX_HEIGHT_PX = 48;
    private static final String TABLE = "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
            + "style=\"border-collapse:collapse\">";

    private EmailSignatureRenderer() {
    }

    public static RenderedSignature render(SignatureContent content, SignatureSender sender) {
        Details details = Details.of(content.fields(), sender);
        String photoUrl = content.layout().drawsPhoto() && content.showPhoto()
                ? sender.photoUrl(content.photoShape()) : null;
        SignatureLogo logo = content.layout().drawsLogo() && content.showLogo() ? sender.logo() : null;
        String booking = content.showBookingLink() ? sender.bookingLink() : null;

        String body = switch (content.layout()) {
            case CLASSIC -> stacked(details, null);
            case PHOTO, PHOTO_LOGO -> beside(details, photoUrl, content.photoShape(), logo);
            case LOGO_BANNER -> stacked(details, logo);
            case MINIMAL -> minimal(details);
            case CUSTOM -> custom(content.customBody(), logo, sender);
        };
        StringBuilder html = new StringBuilder("<div style=\"margin-top:16px;").append(FONT).append("\">").append(body);
        if (content.layout() != SignatureLayout.CUSTOM && hasText(content.customBody())) {
            html.append("<div style=\"margin-top:8px\">")
                    .append(OutreachMarkdownEmail.signatureHtmlOf(content.customBody(), sender.pictures()))
                    .append("</div>");
        }
        html.append(footer(content.footerPictures(), sender));
        if (booking != null) {
            html.append("<div style=\"margin-top:8px;font-weight:bold\">")
                    .append(anchor(booking, "Book a call with me")).append("</div>");
        }
        if (hasText(content.disclaimer())) {
            html.append("<div style=\"margin-top:12px;font-size:11px;line-height:1.4;").append(MUTED).append("\">")
                    .append(escapedLines(content.disclaimer())).append("</div>");
        }
        html.append("</div>");
        return new RenderedSignature(html.toString());
    }

    /** The details one under another, and a logo beneath them past a rule. */
    private static String stacked(Details details, SignatureLogo logo) {
        StringBuilder html = new StringBuilder(TABLE);
        for (String line : details.lines(false)) {
            html.append("<tr><td style=\"").append(FONT).append("\">").append(line).append("</td></tr>");
        }
        if (logo != null) {
            html.append("<tr><td style=\"padding-top:10px\"><div style=\"border-top:2px solid #1f2328;padding-top:10px\">")
                    .append(image(logo)).append("</div></td></tr>");
        }
        return html.append("</table>").toString();
    }

    /**
     * The photo, then the details, then the logo past a vertical rule. The photo's shape is in its pixels; the radius is
     * for a client that scales it, so its edge stays smooth.
     */
    private static String beside(Details details, String photoUrl, PhotoShape shape, SignatureLogo logo) {
        StringBuilder html = new StringBuilder(TABLE).append("<tr>");
        if (photoUrl != null) {
            html.append("<td style=\"vertical-align:middle;padding-right:14px\"><img src=\"").append(escape(photoUrl))
                    .append("\" width=\"").append(PHOTO_PX).append("\" height=\"").append(PHOTO_PX)
                    .append("\" alt=\"\" style=\"display:block;width:").append(PHOTO_PX).append("px;height:")
                    .append(PHOTO_PX).append("px;border-radius:").append(radiusOf(shape)).append(";border:0\"></td>");
        }
        html.append("<td style=\"vertical-align:middle;").append(FONT).append("\">")
                .append(String.join("<br>", details.lines(true))).append("</td>");
        if (logo != null) {
            html.append("<td style=\"vertical-align:middle;padding-left:14px\">")
                    .append("<div style=\"border-left:1px solid #d0d7de;padding-left:14px\">")
                    .append(image(logo)).append("</div></td>");
        }
        return html.append("</tr></table>").toString();
    }

    private static String radiusOf(PhotoShape shape) {
        return switch (shape) {
            case CIRCLE -> "50%";
            case ROUNDED -> "10px";
            case SQUARE -> "0";
        };
    }

    /** The signer's own pictures in one row, each fitted to a logo's box; a token not theirs draws nothing. */
    private static String footer(List<String> tokens, SignatureSender sender) {
        StringBuilder row = new StringBuilder();
        for (String token : tokens) {
            SignaturePicture picture = sender.pictures().get(token);
            if (picture == null) {
                continue;
            }
            double scale = Math.min(1.0, Math.min((double) FOOTER_PICTURE_MAX_WIDTH_PX / picture.width(),
                    (double) FOOTER_PICTURE_MAX_HEIGHT_PX / picture.height()));
            int width = Math.max(1, (int) Math.round(picture.width() * scale));
            int height = Math.max(1, (int) Math.round(picture.height() * scale));
            row.append("<td style=\"vertical-align:middle;padding-right:16px\"><img src=\"").append(escape(picture.url()))
                    .append("\" width=\"").append(width).append("\" height=\"").append(height)
                    .append("\" alt=\"\" style=\"display:block;width:").append(width).append("px;height:").append(height)
                    .append("px;border:0\"></td>");
        }
        if (row.isEmpty()) {
            return "";
        }
        return "<div style=\"margin-top:12px\">" + TABLE + "<tr>" + row + "</tr></table></div>";
    }

    private static String minimal(Details details) {
        StringJoiner line = new StringJoiner(" · ");
        if (details.name != null) {
            line.add("<strong>" + escape(details.name) + "</strong>");
        }
        String role = details.roleLine();
        if (role != null) {
            line.add(escape(role));
        }
        if (details.phone != null) {
            line.add(phoneAnchor(details.phone));
        }
        return "<div>" + line + "</div>";
    }

    /** The person's own Markdown under the composer's rules — no raw HTML, no picture — then the logo, if asked for. */
    private static String custom(String customBody, SignatureLogo logo, SignatureSender sender) {
        String html = hasText(customBody) ? OutreachMarkdownEmail.signatureHtmlOf(customBody, sender.pictures()) : "";
        return logo == null ? html : html + "<div style=\"margin-top:10px\">" + image(logo) + "</div>";
    }

    private static String image(SignatureLogo logo) {
        double scale = Math.min(1.0, Math.min((double) LOGO_MAX_WIDTH_PX / logo.width(),
                (double) LOGO_MAX_HEIGHT_PX / logo.height()));
        int width = Math.max(1, (int) Math.round(logo.width() * scale));
        int height = Math.max(1, (int) Math.round(logo.height() * scale));
        return "<img src=\"" + escape(logo.url()) + "\" width=\"" + width + "\" height=\"" + height
                + "\" alt=\"\" style=\"display:block;width:" + width + "px;height:" + height + "px;border:0\">";
    }

    /**
     * The signature as a Custom signature's Markdown, to go on editing in its place: the details it draws, its photo and
     * logo as the signer's own pictures, its own words kept. The footer's pictures are not in it: they stay the footer. Values are escaped, so a name never becomes
     * markup; a phone stays text, as a Custom signature keeps it.
     */
    public static String asMarkdown(SignatureContent content, SignatureSender sender) {
        String photoUrl = content.layout().drawsPhoto() && content.showPhoto()
                ? sender.photoUrl(content.photoShape()) : null;
        SignatureLogo logo = content.layout().drawsLogo() && content.showLogo() ? sender.logo() : null;
        List<String> blocks = new ArrayList<>();
        if (content.layout() == SignatureLayout.CUSTOM) {
            if (hasText(content.customBody())) {
                blocks.add(content.customBody().strip());
            }
        } else {
            if (photoUrl != null) {
                blocks.add(pictureMarkdown(photoUrl));
            }
            blocks.add(String.join("\\\n", Details.of(content.fields(), sender).markdownLines(
                    content.layout() == SignatureLayout.MINIMAL)));
            if (hasText(content.customBody())) {
                blocks.add(content.customBody().strip());
            }
        }
        if (logo != null) {
            blocks.add(pictureMarkdown(logo.url()));
        }
        return String.join("\n\n", blocks);
    }

    /** Relative, as the editor inserts one: the email's own drawing makes it absolute again. */
    private static String pictureMarkdown(String url) {
        int route = url.indexOf(EmailImageService.PATH);
        return "![](" + (route < 0 ? url : url.substring(route)) + ")";
    }

    /**
     * Inline punctuation and entity references escaped, and what would start a block at the head of a line — "1." or
     * "1)", "-", "=" — escaped too, so a title like "1. Partner" stays text.
     */
    private static String markdownEscaped(String value) {
        return value.replaceAll("([\\\\`*_\\[\\]()<>#+!|~&])", "\\\\$1")
                .replaceFirst("^(\\s*\\d+)([.)])", "$1\\\\$2")
                .replaceFirst("^(\\s*)([-=])", "$1\\\\$2");
    }

    /** The signature's own value where it set one, its source's otherwise; blank is absent either way. */
    private record Details(String name, String title, String company, String phone, String mobile, String email,
                           String website, String linkedinUrl, String address) {

        static Details of(SignatureFields fields, SignatureSender sender) {
            SignatureFields own = fields == null ? SignatureFields.NONE : fields;
            return new Details(either(own.name(), sender.name()), either(own.title(), sender.title()),
                    either(own.company(), sender.company()), either(own.phone(), sender.phone()),
                    either(own.mobile(), null), either(sender.email(), null), either(own.website(), sender.website()),
                    either(own.linkedinUrl(), sender.linkedinUrl()), either(own.address(), null));
        }

        /** "Head of Talent, Al-Futtaim", either half alone, or null. */
        String roleLine() {
            if (title != null && company != null) {
                return title + ", " + company;
            }
            return title != null ? title : company;
        }

        /** As HTML. {@code compact} folds title and company onto one line, beside a photo. */
        List<String> lines(boolean compact) {
            List<String> lines = new ArrayList<>();
            if (name != null) {
                lines.add("<strong style=\"font-size:14px\">" + escape(name) + "</strong>");
            }
            if (compact || title == null || company == null) {
                if (roleLine() != null) {
                    lines.add(muted(escape(roleLine())));
                }
            } else {
                lines.add(muted(escape(title)));
                lines.add(muted(escape(company)));
            }
            StringJoiner reach = new StringJoiner(" · ");
            boolean both = phone != null && mobile != null;
            if (phone != null) {
                reach.add((both ? "T " : "") + phoneAnchor(phone));
            }
            if (mobile != null) {
                reach.add((both ? "M " : "") + phoneAnchor(mobile));
            }
            if (email != null) {
                reach.add(anchor("mailto:" + email, email));
            }
            if (reach.length() > 0) {
                lines.add(muted(reach.toString()));
            }
            StringJoiner web = new StringJoiner(" · ");
            String site = websiteHref(website);
            if (site != null) {
                web.add(anchor(site, displayOf(website)));
            }
            String linkedin = websiteHref(linkedinUrl);
            if (linkedin != null) {
                web.add(anchor(linkedin, "LinkedIn"));
            }
            if (web.length() > 0) {
                lines.add(web.toString());
            }
            if (address != null) {
                lines.add(muted(escape(address)));
            }
            return lines;
        }

        /** The same lines as Markdown, one per hard break; {@code oneLine} is Minimal's. */
        List<String> markdownLines(boolean oneLine) {
            List<String> lines = new ArrayList<>();
            StringJoiner first = new StringJoiner(" · ");
            if (name != null) {
                first.add("**" + markdownEscaped(name) + "**");
            }
            if (oneLine) {
                if (roleLine() != null) {
                    first.add(markdownEscaped(roleLine()));
                }
                if (phone != null) {
                    first.add(markdownEscaped(phone));
                }
                return first.length() > 0 ? List.of(first.toString()) : List.of();
            }
            if (first.length() > 0) {
                lines.add(first.toString());
            }
            if (roleLine() != null) {
                lines.add(markdownEscaped(roleLine()));
            }
            StringJoiner reach = new StringJoiner(" · ");
            boolean both = phone != null && mobile != null;
            if (phone != null) {
                reach.add((both ? "T " : "") + markdownEscaped(phone));
            }
            if (mobile != null) {
                reach.add((both ? "M " : "") + markdownEscaped(mobile));
            }
            if (email != null) {
                reach.add("[" + markdownEscaped(email) + "](<mailto:" + email + ">)");
            }
            if (reach.length() > 0) {
                lines.add(reach.toString());
            }
            StringJoiner web = new StringJoiner(" · ");
            String site = websiteHref(website);
            if (site != null) {
                web.add("[" + markdownEscaped(displayOf(website)) + "](" + site + ")");
            }
            String linkedin = websiteHref(linkedinUrl);
            if (linkedin != null) {
                web.add("[LinkedIn](" + linkedin + ")");
            }
            if (web.length() > 0) {
                lines.add(web.toString());
            }
            if (address != null) {
                lines.add(markdownEscaped(address));
            }
            return lines;
        }

        private static String either(String own, String source) {
            if (hasText(own)) {
                return own.strip();
            }
            return hasText(source) ? source.strip() : null;
        }
    }

    private static String muted(String html) {
        return "<span style=\"" + MUTED + "\">" + html + "</span>";
    }

    private static String anchor(String href, String text) {
        return "<a href=\"" + escape(href) + "\" style=\"" + LINK + "\">" + escape(text) + "</a>";
    }

    /** {@code tel:} takes the digits and a leading plus; a number with none is plain text. */
    private static String phoneAnchor(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return escape(phone);
        }
        return anchor("tel:" + (phone.strip().startsWith("+") ? "+" : "") + digits, phone);
    }

    /** An https address for what was typed — "uncava.com" included — or null for anything but http and https. */
    static String websiteHref(String value) {
        if (!hasText(value)) {
            return null;
        }
        String typed = value.strip();
        String withScheme = typed.matches("(?i)^[a-z][a-z0-9+.-]*:.*") ? typed : "https://" + typed;
        try {
            URI parsed = URI.create(withScheme);
            String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || parsed.getHost() == null
                    || !parsed.getHost().contains(".")) {
                return null;
            }
            return parsed.toString();
        } catch (IllegalArgumentException notAnAddress) {
            return null;
        }
    }

    private static String displayOf(String website) {
        return website.strip().replaceFirst("(?i)^https?://", "").replaceFirst("(?i)^www\\.", "").replaceFirst("/$", "");
    }

    private static String escapedLines(String text) {
        return escape(text.strip()).replace("\r\n", "\n").replace("\n", "<br>");
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
