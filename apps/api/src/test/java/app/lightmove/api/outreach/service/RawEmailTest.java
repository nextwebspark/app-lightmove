package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.outreach.model.OutgoingEmail;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The RFC 2822 message Gmail sends: headers that thread, a subject any client reads, and no injected header. */
class RawEmailTest {

    @Test
    @DisplayName("a follow-up carries In-Reply-To and extends the last message's References")
    void aFollowUpThreads() {
        String text = RawEmail.of(new OutgoingEmail("priya@client.example", "Re: A CFO role", "<p>Hi</p>"),
                "<CAB2@mail.gmail.com>", "<CAB1@mail.gmail.com>").text();

        assertThat(text).contains("To: priya@client.example\r\n")
                .contains("Subject: Re: A CFO role\r\n")
                .contains("In-Reply-To: <CAB2@mail.gmail.com>\r\n")
                .contains("References: <CAB1@mail.gmail.com> <CAB2@mail.gmail.com>\r\n")
                .contains("Content-Type: text/html; charset=UTF-8\r\n");
        String body = text.substring(text.indexOf("\r\n\r\n") + 4).replace("\r\n", "");
        assertThat(new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8)).isEqualTo("<p>Hi</p>");
    }

    @Test
    @DisplayName("a first email names no thread; a non-ASCII subject is RFC 2047 words that decode back whole")
    void subjectsAreEncoded() {
        String text = RawEmail.of(new OutgoingEmail("priya@client.example", "A CFO role", "<p>Hi</p>"), null, null)
                .text();
        assertThat(text).doesNotContain("In-Reply-To").doesNotContain("References");

        String subject = "Directeur financier — Émirats arabes unis — rôle confidentiel pour un groupe régional";
        String encoded = RawEmail.encodedSubject(subject);
        assertThat(encoded).startsWith("=?UTF-8?B?").doesNotContain("\r").doesNotContain("\n");
        StringBuilder decoded = new StringBuilder();
        for (String word : encoded.split(" ")) {
            assertThat(word.length()).isLessThanOrEqualTo(75);
            String payload = word.substring("=?UTF-8?B?".length(), word.length() - 2);
            decoded.append(new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8));
        }
        assertThat(decoded.toString()).isEqualTo(subject);
    }

    @Test
    @DisplayName("a value with a line break can never add a header of its own")
    void headerInjectionIsRefused() {
        assertThatThrownBy(() -> RawEmail.of(new OutgoingEmail("priya@client.example\r\nBcc: spy@evil.example",
                "Hello", "<p>Hi</p>"), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RawEmail.of(new OutgoingEmail("priya@client.example", "Hello\nBcc: spy@evil.example",
                "<p>Hi</p>"), null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a From header reads as its address alone, lower-cased")
    void fromHeadersReadAsAddresses() {
        assertThat(RawEmail.addressOf("\"Priya Raman\" <Priya@Client.example>")).isEqualTo("priya@client.example");
        assertThat(RawEmail.addressOf("mailer-daemon@googlemail.com")).isEqualTo("mailer-daemon@googlemail.com");
        assertThat(RawEmail.addressOf("Undisclosed")).isNull();
    }
}
