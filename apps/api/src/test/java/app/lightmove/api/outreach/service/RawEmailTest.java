package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.outreach.model.EmailAttachment;
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
        String text = sent(RawEmail.of(new OutgoingEmail("priya@client.example", "Re: A CFO role", "<p>Hi</p>"),
                "<CAB2@mail.gmail.com>", "<CAB1@mail.gmail.com>"));

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
        String text = sent(RawEmail.of(new OutgoingEmail("priya@client.example", "A CFO role", "<p>Hi</p>"), null,
                null));
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
    @DisplayName("files make it multipart/mixed: the HTML first, then each file base64 in 76 columns under its name")
    void filesAreMultipartMixed() {
        byte[] cv = new byte[200];
        java.util.Arrays.fill(cv, (byte) 7);
        OutgoingEmail email = new OutgoingEmail("priya@client.example", "The brief", "<p>Attached</p>", null,
                java.util.List.of(new EmailAttachment("Brief.pdf", "application/pdf", "%PDF-1.7".getBytes(
                                StandardCharsets.US_ASCII)),
                        new EmailAttachment("Résumé \"final\".pdf", "application/pdf", cv)));
        String text = sent(RawEmail.of(email, null, null));

        java.util.regex.Matcher boundary = java.util.regex.Pattern
                .compile("Content-Type: multipart/mixed; boundary=\"([^\"]+)\"\r\n").matcher(text);
        assertThat(boundary.find()).isTrue();
        String[] parts = text.split("--" + java.util.regex.Pattern.quote(boundary.group(1)));
        assertThat(parts).hasSize(5);
        assertThat(parts[4]).isEqualTo("--\r\n");
        assertThat(parts[1]).contains("Content-Type: text/html; charset=UTF-8\r\n");
        assertThat(decodedBody(parts[1])).isEqualTo("<p>Attached</p>".getBytes(StandardCharsets.UTF_8));
        assertThat(parts[2]).contains("Content-Type: application/pdf; name=\"Brief.pdf\"\r\n")
                .contains("Content-Disposition: attachment; filename=\"Brief.pdf\"\r\n")
                .doesNotContain("filename*");
        assertThat(decodedBody(parts[2])).isEqualTo("%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        assertThat(parts[3]).contains("filename=\"R_sum_ _final_.pdf\"")
                .contains("filename*=UTF-8''R%C3%A9sum%C3%A9%20%22final%22.pdf");
        assertThat(decodedBody(parts[3])).isEqualTo(cv);
        String encodedLines = parts[3].substring(parts[3].indexOf("\r\n\r\n") + 4);
        assertThat(encodedLines.split("\r\n")).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(76));
    }

    private static byte[] decodedBody(String part) {
        return Base64.getMimeDecoder().decode(part.substring(part.indexOf("\r\n\r\n") + 4).strip());
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

    /** What Gmail receives: the message as URL-safe Base64, decoded back. */
    private static String sent(RawEmail email) {
        return new String(Base64.getUrlDecoder().decode(email.encoded()), StandardCharsets.UTF_8);
    }
}
