package app.lightmove.api.core.email.render;

import static app.lightmove.api.core.email.render.EmailPhrase.plain;
import static app.lightmove.api.core.email.render.EmailPhrase.strong;
import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.email.model.EmailMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The house style, pinned. The renderer exists so that a template states its copy once and both halves
 * of the email are produced from it — these tests are what keeps that true, and what keeps a name
 * someone typed from becoming markup in a colleague's inbox.
 */
class EmailRendererTest {

    private static final String BASE_URL = "https://beta.uncava.com";

    private final EmailRenderer renderer = new EmailRenderer(BASE_URL);

    @Nested
    @DisplayName("a user-supplied value")
    class SuppliedValues {

        @Test
        @DisplayName("is escaped in the HTML half and left alone in the text half")
        void escapesOnlyTheHtmlHalf() {
            EmailMessage message = render(EmailParagraph.of("Hi %s — welcome.",
                    plain("<script>alert(1)</script>")));

            assertThat(message.htmlBody())
                    .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                    .doesNotContain("<script>");
            assertThat(message.textBody()).contains("<script>alert(1)</script>");
        }

        @Test
        @DisplayName("reaches both halves, because both are rendered from the one sentence")
        void reachesBothHalves() {
            EmailMessage message = render(EmailParagraph.of("Hi %s — welcome.", plain("Nadia")));

            assertThat(message.htmlBody()).contains("Hi Nadia — welcome.");
            assertThat(message.textBody()).contains("Hi Nadia — welcome.");
        }

        @Test
        @DisplayName("is emphasised in HTML and unmarked in text")
        void emphasisesOnlyTheHtmlHalf() {
            EmailMessage message = render(EmailParagraph.of("The %s search.", strong("Group CFO")));

            assertThat(message.htmlBody()).contains("The <strong>Group CFO</strong> search.");
            assertThat(message.textBody()).contains("The Group CFO search.");
        }
    }

    @Nested
    @DisplayName("the call to action")
    class Action {

        @Test
        @DisplayName("is a button in HTML and the bare link in text")
        void rendersBothWays() {
            EmailMessage message = render(new EmailAction("Open the search", BASE_URL + "/projects/7"));

            assertThat(message.htmlBody())
                    .contains("<a href=\"https://beta.uncava.com/projects/7\"")
                    .contains(">Open the search</a>");
            assertThat(message.textBody()).contains("Open the search:\nhttps://beta.uncava.com/projects/7");
        }
    }

    @Nested
    @DisplayName("the layout")
    class Layout {

        @Test
        @DisplayName("carries the mark, and the wordmark that stands in for it when images are blocked")
        void carriesTheBrand() {
            EmailMessage message = render(EmailParagraph.of("Hi %s.", plain("Nadia")));

            assertThat(message.htmlBody())
                    .contains("src=\"https://beta.uncava.com/brand/uncava-mark-email-v1.png\"")
                    .contains("alt=\"Uncava\"")
                    .contains(">UNCAVA</span>");
        }

        @Test
        @DisplayName("keeps its markup out of the text half")
        void textHalfIsPlain() {
            EmailMessage message = render(
                    EmailParagraph.of("Hi %s.", plain("Nadia")),
                    new EmailAction("Open the search", BASE_URL + "/projects/7"),
                    EmailNote.of("This link expires in 24 hours."));

            assertThat(message.textBody())
                    .doesNotContain("<")
                    .contains("Welcome")
                    .contains("This link expires in 24 hours.");
        }
    }

    private EmailMessage render(EmailBlock... blocks) {
        return renderer.render("nadia@auroracap.ae", "A subject", EmailContent.of("Welcome", blocks));
    }
}
