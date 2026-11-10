package app.lightmove.api.core.export.service;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A zip written as it is built, entry by entry, so an export of a whole workspace — or of one person, #919 — never sits
 * in memory whole. Names come from what people typed (a position's title, a person's name, a file's own name), so every
 * path segment is folded to a safe slug, never a {@code ..} or a separator, and a name used twice gets a suffix rather
 * than a second entry a reader would silently drop.
 */
public class ZipArchiveWriter implements Closeable {

    private static final int MAX_SEGMENT = 80;

    private final CountingOutputStream counted;
    private final ZipOutputStream zip;
    private final Set<String> written = new HashSet<>();

    public ZipArchiveWriter(OutputStream target) {
        this.counted = new CountingOutputStream(target);
        this.zip = new ZipOutputStream(counted, StandardCharsets.UTF_8);
    }

    /** A folder or file name from free text: lower-case letters, digits and dashes, never empty. */
    public static String slugOf(String text, String fallback) {
        String folded = text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (folded.length() > MAX_SEGMENT) {
            folded = folded.substring(0, MAX_SEGMENT).replaceAll("-+$", "");
        }
        return folded.isEmpty() ? fallback : folded;
    }

    /** A file's own name kept readable, its extension included; only the characters no archive tool agrees on go. */
    public static String fileNameOf(String name, String fallback) {
        String cleaned = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        cleaned = cleaned.replaceAll("^\\.+", "");
        if (cleaned.length() > 120) {
            int dot = cleaned.lastIndexOf('.');
            String extension = dot > 0 && cleaned.length() - dot <= 10 ? cleaned.substring(dot) : "";
            cleaned = cleaned.substring(0, 120 - extension.length()) + extension;
        }
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    public void putText(String path, String text) throws IOException {
        begin(path);
        Writer writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
        writer.write(text);
        writer.flush();
        zip.closeEntry();
    }

    /** Answers the path the entry was written under, a suffix added where the name was already taken. */
    public String putStream(String path, InputStream content) throws IOException {
        String unique = begin(path);
        content.transferTo(zip);
        zip.closeEntry();
        return unique;
    }

    /** An entry the caller writes text into; it ends when the writer is closed, which never closes the archive. */
    public Writer openText(String path) throws IOException {
        begin(path);
        return new OutputStreamWriter(new EntryStream(), StandardCharsets.UTF_8);
    }

    public long bytesWritten() {
        return counted.count;
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    private String begin(String path) throws IOException {
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\")
                || Arrays.stream(path.split("/")).anyMatch(segment -> segment.isEmpty() || segment.equals("..")
                        || segment.equals("."))) {
            throw new IllegalArgumentException("not a relative archive path: " + path);
        }
        String unique = path;
        for (int copy = 2; !written.add(unique.toLowerCase(Locale.ROOT)); copy++) {
            int dot = path.lastIndexOf('.');
            int slash = path.lastIndexOf('/');
            unique = dot > slash + 1
                    ? path.substring(0, dot) + " (" + copy + ")" + path.substring(dot)
                    : path + " (" + copy + ")";
        }
        zip.putNextEntry(new ZipEntry(unique));
        return unique;
    }

    /** Writes into the current entry and closes only it. */
    private final class EntryStream extends OutputStream {

        @Override
        public void write(int value) throws IOException {
            zip.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            zip.write(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            zip.closeEntry();
        }
    }

    private static final class CountingOutputStream extends OutputStream {

        private final OutputStream target;
        private long count;

        private CountingOutputStream(OutputStream target) {
            this.target = target;
        }

        @Override
        public void write(int value) throws IOException {
            target.write(value);
            count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            target.write(bytes, offset, length);
            count += length;
        }

        @Override
        public void flush() throws IOException {
            target.flush();
        }

        @Override
        public void close() throws IOException {
            target.close();
        }
    }
}
