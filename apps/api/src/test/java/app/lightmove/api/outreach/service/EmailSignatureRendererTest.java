package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.outreach.constant.SignatureLayout;
import app.lightmove.api.outreach.model.RenderedSignature;
import app.lightmove.api.outreach.model.SignatureContent;
import app.lightmove.api.outreach.model.SignatureFields;
import app.lightmove.api.outreach.model.SignatureLogo;
import app.lightmove.api.outreach.model.SignatureSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What every layout draws, and what none of them ever lets through. */
class EmailSignatureRendererTest {

    private static final String PHOTO = "https://beta.uncava.com/api/v1/email-images/photo-token";
    private static final SignatureLogo LOGO = new SignatureLogo("https://beta.uncava.com/api/v1/email-images/logo-token",
            480, 120);
    private static final SignatureSender YARA = new SignatureSender("Yara Haddad", "Head of Talent Acquisition",
            "+971 50 123 4567", "https://www.linkedin.com/in/yarahaddad", "yara@alfuttaim.com", "Al-Futtaim",
            "alfuttaim.com", PHOTO, LOGO, "https://beta.uncava.com/book/yara-haddad-ab12cd34");

    private static SignatureContent content(SignatureLayout layout, SignatureFields fields, boolean photo, boolean logo,
                                            boolean booking) {
        return new SignatureContent("Formal", layout, fields, photo, logo, booking, null, null);
    }

    @Test
    @DisplayName("a field the signature leaves empty follows the profile and the workspace")
    void followsItsSources() {
        RenderedSignature rendered = EmailSignatureRenderer.render(
                content(SignatureLayout.CLASSIC, SignatureFields.NONE, false, false, false), YARA);

        assertThat(rendered.html()).contains("Yara Haddad", "Head of Talent Acquisition", "Al-Futtaim",
                "href=\"tel:+971501234567\"", "href=\"mailto:yara@alfuttaim.com\"", "href=\"https://alfuttaim.com\"",
                "href=\"https://www.linkedin.com/in/yarahaddad\"");
        assertThat(rendered.html()).doesNotContain("<img");
        assertThat(rendered.text()).startsWith("-- \nYara Haddad\nHead of Talent Acquisition\nAl-Futtaim");
    }

    @Test
    @DisplayName("an override wins over its source, and a blank one does not")
    void overridesWin() {
        SignatureFields own = new SignatureFields("Yara H.", " ", null, null, "Al-Futtaim Group", null,
                "Festival Tower, Dubai");
        String html = EmailSignatureRenderer.render(content(SignatureLayout.CLASSIC, own, false, false, false), YARA).html();

        assertThat(html).contains("Yara H.", "Head of Talent Acquisition", "Al-Futtaim Group", "Festival Tower, Dubai");
        assertThat(html).doesNotContain("Yara Haddad");
    }

    @Test
    @DisplayName("the photo and logo show only where the layout draws them, they are asked for, and they exist")
    void picturesOnlyWhereAsked() {
        String both = EmailSignatureRenderer.render(
                content(SignatureLayout.PHOTO_LOGO, SignatureFields.NONE, true, true, false), YARA).html();
        assertThat(both).contains("src=\"" + PHOTO + "\"", "width=\"64\" height=\"64\"",
                "src=\"" + LOGO.url() + "\"", "width=\"192\" height=\"48\"");

        String notAsked = EmailSignatureRenderer.render(
                content(SignatureLayout.PHOTO_LOGO, SignatureFields.NONE, false, false, false), YARA).html();
        assertThat(notAsked).doesNotContain("<img");

        String classicAskedAnyway = EmailSignatureRenderer.render(
                content(SignatureLayout.CLASSIC, SignatureFields.NONE, true, true, false), YARA).html();
        assertThat(classicAskedAnyway).doesNotContain("<img");

        SignatureSender noPictures = new SignatureSender("Yara Haddad", null, null, null, "yara@alfuttaim.com", null,
                null, null, null, null);
        String none = EmailSignatureRenderer.render(
                content(SignatureLayout.PHOTO_LOGO, SignatureFields.NONE, true, true, true), noPictures).html();
        assertThat(none).doesNotContain("<img", "Book a call");
    }

    @Test
    @DisplayName("every value is escaped, and a link goes only to http, https, mailto or tel")
    void escapesEverything() {
        SignatureFields hostile = new SignatureFields("<script>alert(1)</script>", "\"><img src=x onerror=alert(1)>",
                null, null, "A & B", "javascript:alert(1)", null);
        String html = EmailSignatureRenderer.render(
                content(SignatureLayout.PHOTO, hostile, true, false, false), YARA).html();

        assertThat(html).doesNotContain("<script", "<img src=x", "javascript:");
        assertThat(html).contains("&lt;script&gt;", "A &amp; B");
    }

    @Test
    @DisplayName("a custom signature is the person's Markdown with no raw HTML and no picture, and the logo if asked")
    void customIsMarkdownOnly() {
        SignatureContent custom = new SignatureContent("Mine", SignatureLayout.CUSTOM, SignatureFields.NONE, false, true,
                false, "Kind regards,\n\n**Yara** <b>raw</b> ![x](https://evil.example/pixel.png) [site](javascript:x)",
                "Confidential.");
        RenderedSignature rendered = EmailSignatureRenderer.render(custom, YARA);

        assertThat(rendered.html()).contains("<strong>Yara</strong>", "&lt;b&gt;raw&lt;/b&gt;", "src=\"" + LOGO.url() + "\"",
                "Confidential.");
        assertThat(rendered.html()).doesNotContain("evil.example", "javascript:");
        assertThat(rendered.text()).contains("Kind regards,", "Confidential.");
    }

    @Test
    @DisplayName("minimal is one line, and the booking link is the consultant's own")
    void minimalAndBooking() {
        RenderedSignature rendered = EmailSignatureRenderer.render(
                content(SignatureLayout.MINIMAL, SignatureFields.NONE, true, true, true), YARA);

        assertThat(rendered.html()).contains("<strong>Yara Haddad</strong> · Head of Talent Acquisition, Al-Futtaim",
                "href=\"https://beta.uncava.com/book/yara-haddad-ab12cd34\"");
        assertThat(rendered.text()).contains("Yara Haddad · Head of Talent Acquisition, Al-Futtaim · +971 50 123 4567",
                "Book a call with me: https://beta.uncava.com/book/yara-haddad-ab12cd34");
    }
}
