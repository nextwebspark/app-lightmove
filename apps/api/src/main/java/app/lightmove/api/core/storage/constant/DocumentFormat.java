package app.lightmove.api.core.storage.constant;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * The file formats a document library accepts, each recognised by its <b>bytes</b> and its extension
 * together. The multipart part's content type is the sender's claim and decides nothing: an executable
 * renamed {@code cv.pdf} has neither a PDF's signature nor a reason to be stored.
 *
 * <p>DOCX and ODT are both zip archives, so the signature tells them from everything else and the
 * extension tells them from each other. Plain text has no signature; it is text if its opening bytes
 * hold no NUL.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum DocumentFormat {

    PDF("application/pdf", Set.of("pdf"), signature('%', 'P', 'D', 'F', '-'), true),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx"),
            signature(0x50, 0x4B, 0x03, 0x04), false),
    ODT("application/vnd.oasis.opendocument.text", Set.of("odt"), signature(0x50, 0x4B, 0x03, 0x04), false),
    DOC("application/msword", Set.of("doc"), signature(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1), false),
    RTF("application/rtf", Set.of("rtf"), signature('{', '\\', 'r', 't', 'f'), false),
    TXT("text/plain", Set.of("txt"), new byte[0], false),
    PNG("image/png", Set.of("png"), signature(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A), true),
    JPEG("image/jpeg", Set.of("jpg", "jpeg"), signature(0xFF, 0xD8, 0xFF), true);

    /** How many opening bytes {@link #detect} needs to see. */
    public static final int HEADER_LENGTH = 512;

    private final String contentType;
    private final Set<String> extensions;
    private final byte[] signature;

    /** A browser shows these itself, so a preview may serve them inline; anything else is downloaded. */
    private final boolean previewable;

    /** The format both the name and the bytes agree on, or empty when they don't or neither is offered. */
    public static Optional<DocumentFormat> detect(String fileName, byte[] header) {
        String extension = extensionOf(fileName);
        return Arrays.stream(values())
                .filter(format -> format.extensions.contains(extension))
                .filter(format -> format.matches(header))
                .findFirst();
    }

    public static String extensionOf(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean matches(byte[] header) {
        if (this == TXT) {
            return header.length > 0 && !containsNul(header);
        }
        if (header.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsNul(byte[] header) {
        for (byte value : header) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    private static byte[] signature(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }
}
