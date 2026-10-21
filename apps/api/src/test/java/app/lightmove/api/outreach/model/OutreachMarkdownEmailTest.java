package app.lightmove.api.outreach.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutreachMarkdownEmailTest {

    @Test
    @DisplayName("what Markdown means becomes markup: emphasis, lists, a quote, an http link")
    void markdownBecomesMarkup() {
        String html = OutreachMarkdownEmail.htmlOf("Hi **Priya**,\n\n- one\n- two\n\n> a quote\n\n"
                + "[our brief](https://beta.uncava.com/brief) and ++this++");

        assertThat(html).contains("<strong>Priya</strong>", "<li>one</li>", "<blockquote>",
                "href=\"https://beta.uncava.com/brief\">our brief</a>", "<ins>this</ins>");
    }

    @Test
    @DisplayName("nothing typed becomes markup but Markdown: raw HTML is text, a script never runs")
    void rawHtmlIsText() {
        String html = OutreachMarkdownEmail.htmlOf("<script>alert(1)</script>\n\n<b onclick=\"x()\">hi</b>");

        assertThat(html).doesNotContain("<script>", "<b ").contains("&lt;script&gt;", "&lt;b onclick=");
    }

    @Test
    @DisplayName("a link goes only to http, https or mailto, and an image is its alt text")
    void linksAndImagesAreHeld() {
        String html = OutreachMarkdownEmail.htmlOf("[click](javascript:alert(1)) [file](file:///etc/passwd) "
                + "[mail](mailto:yara@firm.example) ![a tracking pixel](https://tracker.example/p.gif)");

        assertThat(html).doesNotContain("javascript:", "file:", "<img", "tracker.example")
                .contains("href=\"mailto:yara@firm.example\"", "a tracking pixel");
    }

    @Test
    @DisplayName("a mention reaches the executive as a name, never the id behind it")
    void mentionsAreNames() {
        String markdown = OutreachMarkdownEmail.wordsOf(
                "Copying @[Omar Khalil](user:7f3d2a1e-0000-0000-0000-000000000001) in.");

        assertThat(markdown).isEqualTo("Copying @Omar Khalil in.");
        assertThat(OutreachMarkdownEmail.htmlOf(markdown)).doesNotContain("user:", "7f3d2a1e");
    }

    @Test
    @DisplayName("a token's value is the profile's words, never Markdown: it cannot become a link")
    void tokenValuesAreEscaped() {
        SequenceTokens tokens = new SequenceTokens("Priya", "CFO", "[Acme](https://evil.example) *Group*", "Group CFO",
                "Dubai", "Yara", null, "https://beta.uncava.com/book/yara-1a2b3c4d");

        String html = OutreachMarkdownEmail.htmlOf(tokens.renderMarkdown(
                "At {{currentCompany}}? Pick a time: {{ bookingLink }}"));

        assertThat(html).doesNotContain("evil.example\">", "<em>")
                .contains("[Acme](https://evil.example) *Group*", "href=\"https://beta.uncava.com/book/yara-1a2b3c4d\">");
    }

    @Test
    @DisplayName("the booking link, wrapped as an autolink, is an anchor")
    void theBookingLinkIsAnAnchor() {
        assertThat(OutreachMarkdownEmail.htmlOf("Pick a time: <https://beta.uncava.com/book/yara-1a2b3c4d>"))
                .contains("href=\"https://beta.uncava.com/book/yara-1a2b3c4d\">");
    }
}
