package app.lightmove.api.outreach.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutreachEmailBodyTest {

    @Test
    @DisplayName("paragraphs and line breaks become markup, and everything else is escaped")
    void plainTextBecomesSafeHtml() {
        assertThat(OutreachEmailBody.htmlOf("Hi Priya,\r\n\r\nYour <b>move</b> & more\nstood out.\n\n\n\nYara"))
                .isEqualTo("<p>Hi Priya,</p><p>Your &lt;b&gt;move&lt;/b&gt; &amp; more<br>stood out.</p><p>Yara</p>");
        assertThat(OutreachEmailBody.htmlOf(null)).isEmpty();
    }

    @Test
    @DisplayName("the sender's booking link becomes the one anchor; any other URL stays text")
    void onlyTheBookingLinkIsAnAnchor() {
        String link = "https://beta.uncava.com/book/yara-haddad";

        assertThat(OutreachEmailBody.htmlOf("Pick a time: " + link + "\n\nSee https://evil.example", link))
                .isEqualTo("<p>Pick a time: <a href=\"" + link + "\">" + link + "</a></p><p>See https://evil.example</p>");
        assertThat(OutreachEmailBody.htmlOf("No link here", link)).isEqualTo("<p>No link here</p>");
    }
}
