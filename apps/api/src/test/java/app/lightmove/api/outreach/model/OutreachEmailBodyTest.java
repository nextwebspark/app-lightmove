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

    @Test
    @DisplayName("the signature goes where the token places it, and a text placing none is unsigned")
    void theSignatureGoesWhereTheTokenPlacesIt() {
        String signature = "<table>Yara</table>";

        assertThat(OutreachEmailBody.htmlOf("Hi Priya,\n\n{{ signature }}\n\nP.S. <soon>", null, signature))
                .isEqualTo("<p>Hi Priya,</p><table>Yara</table><p>P.S. &lt;soon&gt;</p>");
        assertThat(OutreachEmailBody.htmlOf("Hi Priya", null, signature)).isEqualTo("<p>Hi Priya</p>");
        assertThat(OutreachEmailBody.htmlOf("Hi Priya", null, null)).isEqualTo("<p>Hi Priya</p>");
        assertThat(OutreachEmailBody.withoutSignatures("Thanks,\n\n{{signature}}")).isEqualTo("Thanks,");
        assertThat(OutreachEmailBody.withoutSignatures("Hi,\n\n{{signature}}\n\nP.S. Soon")).isEqualTo("Hi,\n\nP.S. Soon");
    }
}
