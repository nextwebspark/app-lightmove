package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.storage.constant.PictureShape;
import app.lightmove.api.outreach.constant.SignatureLayout;
import app.lightmove.api.outreach.model.FooterPicture;
import app.lightmove.api.outreach.model.RenderedSignature;
import app.lightmove.api.outreach.model.SignatureContent;
import app.lightmove.api.outreach.model.SignatureFields;
import app.lightmove.api.outreach.model.SignatureLogo;
import app.lightmove.api.outreach.model.SignaturePicture;
import app.lightmove.api.outreach.model.SignatureSender;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What every layout draws, and what none of them ever lets through. */
class EmailSignatureRendererTest {

    private static final String PHOTO = "https://beta.uncava.com/api/v1/email-images/photo-token";
    private static final SignatureLogo LOGO = new SignatureLogo("https://beta.uncava.com/api/v1/email-images/logo-token",
            480, 120);
    private static final SignatureSender YARA = new SignatureSender("Yara Haddad", "Head of Talent Acquisition",
            "+971 50 123 4567", "https://www.linkedin.com/in/yarahaddad", "yara@alfuttaim.com", "Al-Futtaim",
            "alfuttaim.com", Map.of(PictureShape.CIRCLE, PHOTO, PictureShape.ROUNDED, PHOTO + "-rounded"), LOGO, "https://beta.uncava.com/book/yara-haddad-ab12cd34", Map.of());

    private static SignatureContent signature(String name, SignatureLayout layout, SignatureFields fields,
                                              boolean photo, boolean logo, boolean booking, String customBody,
                                              String disclaimer) {
        return signature(name, layout, fields, photo, PictureShape.CIRCLE, logo, booking, customBody, List.of(),
                disclaimer);
    }

    private static SignatureContent signature(String name, SignatureLayout layout, SignatureFields fields,
                                              boolean photo, PictureShape shape, boolean logo, boolean booking,
                                              String customBody, List<FooterPicture> footer, String disclaimer) {
        return new SignatureContent(name, layout, fields, photo, shape, logo, booking, customBody, footer, disclaimer);
    }

    private static SignatureContent content(SignatureLayout layout, SignatureFields fields, boolean photo, boolean logo,
                                            boolean booking) {
        return signature("Formal", layout, fields, photo, logo, booking, null, null);
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
    }

    @Test
    @DisplayName("an override wins over its source, and a blank one does not")
    void overridesWin() {
        SignatureFields own = new SignatureFields("Yara H.", " ", null, null, "Al-Futtaim Group", null,
                "Festival Tower, Dubai", null);
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
                null, Map.of(), null, null, Map.of());
        String none = EmailSignatureRenderer.render(
                content(SignatureLayout.PHOTO_LOGO, SignatureFields.NONE, true, true, true), noPictures).html();
        assertThat(none).doesNotContain("<img", "Book a call");
    }

    @Test
    @DisplayName("every value is escaped, and a link goes only to http, https, mailto or tel")
    void escapesEverything() {
        SignatureFields hostile = new SignatureFields("<script>alert(1)</script>", "\"><img src=x onerror=alert(1)>",
                null, null, "A & B", "javascript:alert(1)", null, null);
        String html = EmailSignatureRenderer.render(
                content(SignatureLayout.PHOTO, hostile, true, false, false), YARA).html();

        assertThat(html).doesNotContain("<script", "<img src=x", "javascript:");
        assertThat(html).contains("&lt;script&gt;", "A &amp; B");
    }

    @Test
    @DisplayName("a custom signature is the person's Markdown with no raw HTML and no picture, and the logo if asked")
    void customIsMarkdownOnly() {
        SignatureContent custom = signature("Mine", SignatureLayout.CUSTOM, SignatureFields.NONE, false, true,
                false, "Kind regards,\n\n**Yara** <b>raw</b> ![x](https://evil.example/pixel.png) [site](javascript:x)",
                "Confidential.");
        RenderedSignature rendered = EmailSignatureRenderer.render(custom, YARA);

        assertThat(rendered.html()).contains("<strong>Yara</strong>", "&lt;b&gt;raw&lt;/b&gt;", "src=\"" + LOGO.url() + "\"",
                "Confidential.");
        assertThat(rendered.html()).doesNotContain("evil.example", "javascript:");
        assertThat(rendered.html()).contains("Kind regards,");
    }

    @Test
    @DisplayName("a custom signature draws the signer's own pictures in a row at a fixed size, at most six, nothing else")
    void customDrawsOwnPictures() {
        String own = "https://beta.uncava.com/api/v1/email-images/" + "a".repeat(43);
        String other = "/api/v1/email-images/" + "b".repeat(43);
        SignatureSender withPictures = new SignatureSender("Yara Haddad", null, null, null, "yara@alfuttaim.com", null,
                null, Map.of(), null, null, Map.of("a".repeat(43), new SignaturePicture(own, 1200, 300)));
        String markdown = "![Group](/api/v1/email-images/" + "a".repeat(43) + " \"large\") ![Not mine](" + other + ")";
        String html = EmailSignatureRenderer.render(signature("Mine", SignatureLayout.CUSTOM,
                SignatureFields.NONE, false, false, false, markdown, null), withPictures).html();

        assertThat(html).contains("<img src=\"" + own + "\" width=\"600\" height=\"150\" alt=\"Group\"",
                "Not mine").doesNotContain("b".repeat(43));

        String eight = ("![](/api/v1/email-images/" + "a".repeat(43) + ") ").repeat(8);
        String capped = EmailSignatureRenderer.render(signature("Mine", SignatureLayout.CUSTOM,
                SignatureFields.NONE, false, false, false, eight, null), withPictures).html();
        assertThat(capped.split("<img", -1)).hasSize(SignaturePicture.MAX_PER_SIGNATURE + 1);
    }

    @Test
    @DisplayName("as Markdown, no value starts a list or heading or names an entity, and the photo and logo are pictures")
    void asMarkdownKeepsValuesText() {
        SignatureFields typed = new SignatureFields(null, "1. Partner", null, null, "A & B", null, "- Floor 3", null);
        String markdown = EmailSignatureRenderer.asMarkdown(content(SignatureLayout.LOGO_BANNER, typed, false, true,
                false), YARA);

        assertThat(markdown).contains("1\\. Partner, A \\& B", "\\- Floor 3",
                "](<mailto:yara@alfuttaim.com>)", "![](/api/v1/email-images/logo-token)");
        String drawn = EmailSignatureRenderer.render(signature("Mine", SignatureLayout.CUSTOM,
                SignatureFields.NONE, false, false, false, markdown, null), YARA).html();
        assertThat(drawn).contains("1. Partner, A &amp; B", "- Floor 3").doesNotContain("<ol", "<ul", "<h");
    }

    @Test
    @DisplayName("the photo is drawn in the shape asked for")
    void photoInItsShape() {
        SignatureContent rounded = signature("Formal", SignatureLayout.PHOTO, SignatureFields.NONE, true,
                PictureShape.ROUNDED, false, false, null, List.of(), null);
        String html = EmailSignatureRenderer.render(rounded, YARA).html();

        assertThat(html).contains("src=\"" + PHOTO + "-rounded\"", "border-radius:10px");
    }

    @Test
    @DisplayName("a second number, extra words and footer pictures show in a fixed layout; a picture not theirs does not")
    void extrasInAFixedLayout() {
        String own = "https://beta.uncava.com/api/v1/email-images/" + "a".repeat(43);
        SignatureSender withPictures = new SignatureSender("Yara Haddad", null, "+971 4 111 2222", null,
                "yara@alfuttaim.com", null, null, Map.of(), null, null,
                Map.of("a".repeat(43), new SignaturePicture(own, 480, 240)));
        SignatureFields mobile = new SignatureFields(null, null, null, null, null, null, null, "+971 50 333 4444");
        SignatureContent content = signature("Formal", SignatureLayout.CLASSIC, mobile, false,
                PictureShape.CIRCLE, false, false, "Dubai · **Riyadh**", List.of(new FooterPicture("a".repeat(43), PictureShape.SQUARE),
                        new FooterPicture("b".repeat(43), PictureShape.SQUARE)), null);
        String html = EmailSignatureRenderer.render(content, withPictures).html();

        assertThat(html).contains("T <a href=\"tel:+97141112222\"", "M <a href=\"tel:+971503334444\"",
                "<strong>Riyadh</strong>", "<img src=\"" + own + "\" width=\"96\" height=\"48\"");
        assertThat(html).doesNotContain("b".repeat(43));
    }

    @Test
    @DisplayName("a picture is drawn in the shape it names, in the words and at the foot")
    void picturesInTheirShape() {
        String token = "a".repeat(43);
        String circle = "https://beta.uncava.com/api/v1/email-images/" + "c".repeat(43);
        String rounded = "https://beta.uncava.com/api/v1/email-images/" + "r".repeat(43);
        SignatureSender shaped = new SignatureSender("Yara Haddad", null, null, null, "yara@alfuttaim.com", null,
                null, Map.of(), null, null, Map.of(token + ":CIRCLE", new SignaturePicture(circle, 400, 400),
                        token + ":ROUNDED", new SignaturePicture(rounded, 800, 400)));
        SignatureContent content = signature("Mine", SignatureLayout.CUSTOM, SignatureFields.NONE, false,
                PictureShape.CIRCLE, false, false, "![Me](/api/v1/email-images/" + token + " \"small circle\")",
                List.of(new FooterPicture(token, PictureShape.ROUNDED)), null);
        String html = EmailSignatureRenderer.render(content, shaped).html();

        assertThat(html).contains("<img src=\"" + circle + "\" width=\"40\" height=\"40\"",
                "<img src=\"" + rounded + "\" width=\"96\" height=\"48\"");
    }

    @Test
    @DisplayName("minimal carries a mobile too")
    void minimalCarriesMobile() {
        SignatureFields mobile = new SignatureFields(null, null, null, null, null, null, null, "+971 50 333 4444");
        String html = EmailSignatureRenderer.render(content(SignatureLayout.MINIMAL, mobile, false, false, false), YARA)
                .html();

        assertThat(html).contains("tel:+971501234567", "tel:+971503334444");
    }

    @Test
    @DisplayName("minimal is one line, and the booking link is the consultant's own")
    void minimalAndBooking() {
        RenderedSignature rendered = EmailSignatureRenderer.render(
                content(SignatureLayout.MINIMAL, SignatureFields.NONE, true, true, true), YARA);

        assertThat(rendered.html()).contains("<strong>Yara Haddad</strong> · Head of Talent Acquisition, Al-Futtaim",
                "href=\"https://beta.uncava.com/book/yara-haddad-ab12cd34\"");
        assertThat(rendered.html()).contains("+971 50 123 4567", "Book a call with me");
    }
}
