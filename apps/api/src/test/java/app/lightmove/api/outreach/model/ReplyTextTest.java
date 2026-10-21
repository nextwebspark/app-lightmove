package app.lightmove.api.outreach.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReplyTextTest {

    @Test
    @DisplayName("Gmail's attribution line, wrapped or not, ends the reply")
    void gmailAttributionEndsTheReply() {
        assertThat(ReplyText.ofPlainText("Happy to talk on Thursday.\n\nOn Mon, 20 Oct 2026 at 10:00, Yara Haddad"
                + " <yara@firm.example>\nwrote:\n> Would you be open to a call?"))
                .isEqualTo("Happy to talk on Thursday.");
        assertThat(ReplyText.ofPlainText("Thanks — not now.\r\nOn Mon, Oct 20, 2026 at 10:00 AM Yara wrote:\r\n> Hi"))
                .isEqualTo("Thanks — not now.");
    }

    @Test
    @DisplayName("Outlook's header block and the original-message rule end the reply")
    void outlookHistoryEndsTheReply() {
        assertThat(ReplyText.ofPlainText("Please call my assistant.\n\nFrom: Yara Haddad <yara@firm.example>\n"
                + "Sent: Monday, 20 October 2026 10:00\nTo: Priya\nSubject: A conversation"))
                .isEqualTo("Please call my assistant.");
        assertThat(ReplyText.ofPlainText("Yes.\n-----Original Message-----\nFrom: Yara"))
                .isEqualTo("Yes.");
    }

    @Test
    @DisplayName("a quoted line is dropped, a line that merely mentions From: is not")
    void quotedLinesGo() {
        assertThat(ReplyText.ofPlainText("I agree with this:\n> the role is regional\nFrom: my side, yes."))
                .isEqualTo("I agree with this:\nFrom: my side, yes.");
    }

    @Test
    @DisplayName("a reply that writes nothing of its own is null, never our own email read back as theirs")
    void nothingWrittenIsNull() {
        assertThat(ReplyText.ofPlainText("> Would you be open to a call?")).isNull();
        assertThat(ReplyText.ofHtml("<div>&nbsp;</div><blockquote>Would you be open?</blockquote>")).isNull();
        assertThat(ReplyText.ofPlainText("  \n ")).isNull();
        assertThat(ReplyText.ofPlainText(null)).isNull();
    }

    @Test
    @DisplayName("an answer typed between quoted lines is kept")
    void inlineAnswersStay() {
        assertThat(ReplyText.ofPlainText("See my answers below.\n\nOn Mon, Yara <yara@firm.example> wrote:\n"
                + "> Are you open to Dubai?\nYes, with family.\n> Notice period?\nThree months."))
                .isEqualTo("See my answers below.\n\nYes, with family.\nThree months.");
        assertThat(ReplyText.ofHtml("<div>See my answers inline.</div><div class=\"gmail_quote\">"
                + "<div class=\"gmail_attr\">On Mon, Yara wrote:</div><blockquote>Q1</blockquote><div>A1: yes</div>"
                + "<blockquote>Q2</blockquote><div>A2: three months</div></div>"))
                .isEqualTo("See my answers inline.\nA1: yes\nA2: three months");
    }

    @Test
    @DisplayName("a body sent without its type is read as what it looks like, its lines kept")
    void anUntypedBodyIsReadAsItLooks() {
        assertThat(ReplyText.ofBody("Thanks, yes.\nOn Mon, Priya wrote:\n> hello")).isEqualTo("Thanks, yes.");
        assertThat(ReplyText.ofBody("<p>Thanks, yes.</p><blockquote>hello</blockquote>")).isEqualTo("Thanks, yes.");
    }

    @Test
    @DisplayName("Outlook's header block is cut in the languages Gulf mailboxes are set to")
    void localisedHeadersAreCut() {
        assertThat(ReplyText.ofPlainText("Gerne.\n\nVon: Yara Haddad\nGesendet: Montag\nAn: Priya"))
                .isEqualTo("Gerne.");
        assertThat(ReplyText.ofPlainText("شكراً\n\nمن: Yara Haddad\nالتاريخ: الاثنين")).isEqualTo("شكراً");
    }

    @Test
    @DisplayName("an HTML body is read down to its words, its paragraphs and line breaks kept, its quote removed")
    void htmlIsReadDownToWords() {
        String html = "<html><head><style>p{color:red}</style></head><body><div dir=\"ltr\"><p>Dear Yara,</p>"
                + "<p>Thursday works &amp; I&rsquo;m free<br>after 3pm.</p><script>alert(1)</script></div>"
                + "<div class=\"gmail_quote\"><div>On Mon wrote:</div><blockquote>Would you be open?</blockquote>"
                + "</div></body></html>";

        assertThat(ReplyText.ofHtml(html)).isEqualTo("Dear Yara,\nThursday works & I’m free\nafter 3pm.");
    }

    @Test
    @DisplayName("Outlook on the web's reply header and everything after it go")
    void outlookWebHistoryGoes() {
        String html = "<div>Sounds good.</div><div id=\"appendonsend\"></div><hr><div id=\"divRplyFwdMsg\">"
                + "<b>From:</b> Yara</div><div>Would you be open?</div>";

        assertThat(ReplyText.ofHtml(html)).isEqualTo("Sounds good.");
    }

    @Test
    @DisplayName("the words are held to the ledger's length")
    void heldToLength() {
        assertThat(ReplyText.ofPlainText("a".repeat(OutreachMessage.MAX_TEXT + 50)))
                .hasSize(OutreachMessage.MAX_TEXT);
        assertThat(ReplyText.ofPlainText("a".repeat(OutreachMessage.MAX_TEXT - 1) + "😀"))
                .as("never half an emoji").hasSize(OutreachMessage.MAX_TEXT - 1);
    }
}
