package app.lightmove.api.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.storage.constant.DocumentFormat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A file is accepted only where its name and its bytes agree on one format. */
class DocumentFormatTest {

    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};
    private static final byte[] EXECUTABLE = {0x4D, 0x5A, (byte) 0x90, 0x00};

    @Test
    @DisplayName("a PDF named .pdf is a PDF, previewable")
    void readsAPdf() {
        assertThat(DocumentFormat.detect("Jane Doe CV.PDF", PDF)).contains(DocumentFormat.PDF);
        assertThat(DocumentFormat.PDF.previewable()).isTrue();
    }

    @Test
    @DisplayName("an executable renamed .pdf is nothing")
    void refusesARenamedExecutable() {
        assertThat(DocumentFormat.detect("cv.pdf", EXECUTABLE)).isEmpty();
    }

    @Test
    @DisplayName("a zip is DOCX or ODT by its extension, and nothing under any other")
    void tellsZipFormatsApartByName() {
        assertThat(DocumentFormat.detect("cv.docx", ZIP)).contains(DocumentFormat.DOCX);
        assertThat(DocumentFormat.detect("cv.odt", ZIP)).contains(DocumentFormat.ODT);
        assertThat(DocumentFormat.detect("archive.zip", ZIP)).isEmpty();
        assertThat(DocumentFormat.DOCX.previewable()).isFalse();
    }

    @Test
    @DisplayName("text is text only while it holds no NUL byte")
    void readsPlainText() {
        assertThat(DocumentFormat.detect("notes.txt", "Referee: J. Smith".getBytes(StandardCharsets.UTF_8)))
                .contains(DocumentFormat.TXT);
        assertThat(DocumentFormat.detect("notes.txt", EXECUTABLE)).isEmpty();
        assertThat(DocumentFormat.detect("notes.txt", new byte[0])).isEmpty();
    }

    @Test
    @DisplayName("a name with no extension is refused, whatever its bytes")
    void refusesANameWithNoExtension() {
        assertThat(DocumentFormat.detect("cv", PDF)).isEmpty();
    }
}
