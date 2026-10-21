package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.model.EmailAttachment;
import app.lightmove.api.outreach.model.OutgoingEmail;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * One outreach email as the RFC 2822 message Gmail's {@code messages.send} takes: an HTML part in UTF-8, a subject
 * encoded per RFC 2047 where it is not ASCII, and on a follow-up the {@code In-Reply-To} and {@code References} that
 * thread it in any client. Gmail writes {@code From}, {@code Date} and {@code Message-ID} itself. An email carrying
 * files is {@code multipart/mixed}: the HTML part first, then each file base64-encoded under its name, written per
 * RFC 2231 where it is not ASCII.
 */
final class RawEmail {

    private static final String CRLF = "\r\n";

    /** RFC 2047 caps an encoded word at 75 characters; this many bytes of UTF-8 always fit one. */
    private static final int ENCODED_WORD_BYTES = 45;

    private static final Base64.Encoder MIME_BASE64 = Base64.getMimeEncoder(76, CRLF.getBytes(StandardCharsets.US_ASCII));

    /** RFC 2231's attribute-char: what a parameter value may carry unescaped. */
    private static final String ATTRIBUTE_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#$&+-.^_`|~";

    private final String message;

    private RawEmail(String message) {
        this.message = message;
    }

    /**
     * @param inReplyTo the last message's {@code Message-ID}, or null for a first email
     * @param references that message's own {@code References}, which the new one extends
     */
    static RawEmail of(OutgoingEmail email, String inReplyTo, String references) {
        StringBuilder raw = new StringBuilder();
        header(raw, "To", email.to());
        header(raw, "Subject", encodedSubject(email.subject()));
        if (inReplyTo != null) {
            header(raw, "In-Reply-To", inReplyTo);
            header(raw, "References", references == null ? inReplyTo : references + " " + inReplyTo);
        }
        header(raw, "MIME-Version", "1.0");
        if (!email.hasAttachments()) {
            htmlPart(raw, email.htmlBody());
            return new RawEmail(raw.toString());
        }
        String boundary = "=_uncava_" + UUID.randomUUID().toString().replace("-", "");
        header(raw, "Content-Type", "multipart/mixed; boundary=\"" + boundary + "\"");
        raw.append(CRLF);
        raw.append("--").append(boundary).append(CRLF);
        htmlPart(raw, email.htmlBody());
        for (EmailAttachment attachment : email.attachments()) {
            raw.append("--").append(boundary).append(CRLF);
            attachmentPart(raw, attachment);
        }
        raw.append("--").append(boundary).append("--").append(CRLF);
        return new RawEmail(raw.toString());
    }

    /** What {@code messages.send} takes as {@code raw}. */
    String encoded() {
        return Base64.getUrlEncoder().encodeToString(bytes());
    }

    /** The message itself, as Gmail's upload endpoint takes it ({@code message/rfc822}). Every byte is ASCII. */
    byte[] bytes() {
        return message.getBytes(StandardCharsets.UTF_8);
    }

    private static void htmlPart(StringBuilder raw, String htmlBody) {
        header(raw, "Content-Type", "text/html; charset=UTF-8");
        header(raw, "Content-Transfer-Encoding", "base64");
        raw.append(CRLF);
        raw.append(MIME_BASE64.encodeToString(htmlBody.getBytes(StandardCharsets.UTF_8)));
        raw.append(CRLF);
    }

    private static void attachmentPart(StringBuilder raw, EmailAttachment attachment) {
        String fileName = attachment.fileName();
        header(raw, "Content-Type", attachment.contentType() + "; name=\"" + asciiFallbackOf(fileName) + "\"");
        String disposition = "attachment; filename=\"" + asciiFallbackOf(fileName) + "\"";
        if (!isPlainAscii(fileName)) {
            disposition += "; filename*=UTF-8''" + percentEncoded(fileName);
        }
        header(raw, "Content-Disposition", disposition);
        header(raw, "Content-Transfer-Encoding", "base64");
        raw.append(CRLF);
        raw.append(MIME_BASE64.encodeToString(attachment.content()));
        raw.append(CRLF);
    }

    /** What a client that never learned RFC 2231 shows: the name with anything outside printable ASCII as {@code _}. */
    static String asciiFallbackOf(String fileName) {
        StringBuilder fallback = new StringBuilder();
        fileName.codePoints().forEach(codePoint -> fallback.append(
                codePoint >= 0x20 && codePoint < 0x7f && codePoint != '"' && codePoint != '\\'
                        ? (char) codePoint : '_'));
        return fallback.toString();
    }

    static String percentEncoded(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte octet : value.getBytes(StandardCharsets.UTF_8)) {
            char character = (char) (octet & 0xff);
            if (character < 0x80 && ATTRIBUTE_CHARS.indexOf(character) >= 0) {
                encoded.append(character);
            } else {
                encoded.append('%').append(HexFormat.of().withUpperCase().toHexDigits(octet));
            }
        }
        return encoded.toString();
    }

    private static boolean isPlainAscii(String value) {
        return value.chars().allMatch(character -> character >= 0x20 && character < 0x7f
                && character != '"' && character != '\\');
    }

    /** The address in a {@code From} header, {@code "Name" <a@b>} or bare; lower-cased like every ledger address. */
    static String addressOf(String fromHeader) {
        if (fromHeader == null) {
            return null;
        }
        int open = fromHeader.lastIndexOf('<');
        int close = fromHeader.lastIndexOf('>');
        String address = open >= 0 && close > open ? fromHeader.substring(open + 1, close) : fromHeader;
        address = address.strip();
        return address.contains("@") ? address.toLowerCase(Locale.ROOT) : null;
    }

    /** A header value carrying a line break would let a value add headers of its own; none ever reaches here. */
    private static void header(StringBuilder raw, String name, String value) {
        if (value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("A " + name + " header cannot be blank or span lines");
        }
        raw.append(name).append(": ").append(value).append(CRLF);
    }

    static String encodedSubject(String subject) {
        if (subject.indexOf('\r') >= 0 || subject.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("A subject cannot span lines");
        }
        if (subject.chars().allMatch(character -> character >= 0x20 && character < 0x7f)) {
            return subject;
        }
        StringBuilder encoded = new StringBuilder();
        StringBuilder chunk = new StringBuilder();
        int chunkBytes = 0;
        for (int offset = 0; offset < subject.length(); ) {
            int codePoint = subject.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int bytes = character.getBytes(StandardCharsets.UTF_8).length;
            if (chunkBytes + bytes > ENCODED_WORD_BYTES) {
                appendWord(encoded, chunk);
                chunk.setLength(0);
                chunkBytes = 0;
            }
            chunk.append(character);
            chunkBytes += bytes;
            offset += Character.charCount(codePoint);
        }
        appendWord(encoded, chunk);
        return encoded.toString();
    }

    private static void appendWord(StringBuilder encoded, StringBuilder chunk) {
        if (!encoded.isEmpty()) {
            encoded.append(' ');
        }
        encoded.append("=?UTF-8?B?")
                .append(Base64.getEncoder().encodeToString(chunk.toString().getBytes(StandardCharsets.UTF_8)))
                .append("?=");
    }
}
