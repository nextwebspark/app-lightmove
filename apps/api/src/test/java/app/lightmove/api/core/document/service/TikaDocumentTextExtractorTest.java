package app.lightmove.api.core.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.error.model.ApiException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TikaDocumentTextExtractorTest {

    private final TikaDocumentTextExtractor extractor = new TikaDocumentTextExtractor();

    @Test
    void extractsPlainTextVerbatim() {
        String text = extractor.extract(
                "Chief Financial Officer. Reports to the Group CEO.".getBytes(StandardCharsets.UTF_8),
                "text/plain");

        assertThat(text).contains("Chief Financial Officer", "Reports to the Group CEO");
    }

    @Test
    void unparseableBytesFailWithBriefExtractionFailed() {
        byte[] garbage = {0x25, 0x50, 0x44, 0x46, 0x00, 0x00, 0x00}; // "%PDF" then junk, not a real PDF

        assertThatThrownBy(() -> extractor.extract(garbage, "application/pdf"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode().name())
                .isEqualTo("BRIEF_EXTRACTION_FAILED");
    }

    @Test
    void blankDocumentFailsAsNoUsableText() {
        byte[] blank = "   \n\t  ".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extract(blank, "text/plain"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode().name())
                .isEqualTo("BRIEF_EXTRACTION_FAILED");
    }
}
