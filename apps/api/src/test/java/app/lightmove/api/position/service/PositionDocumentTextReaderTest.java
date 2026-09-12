package app.lightmove.api.position.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PositionExtractionSettings;
import app.lightmove.api.core.config.PositionSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Bytes to text, for every format the extraction feature accepts or refuses.
 *
 * <p>Every cap and refusal here exists because parsing an untrusted document in-process is an attack
 * surface this feature is the first to open — see the class doc on {@link PositionDocumentTextReader}.
 */
class PositionDocumentTextReaderTest {

    @Test
    @DisplayName("the GM-IT fixture reads to a known phrase, with its table's own 2-space separator intact")
    void readsTheGmItFixture() throws Exception {
        String text = readerWith(40_000, 60).read(fixture("Spec--General Manager - IT_vF.pdf"));

        assertThat(text).contains("General Manager").contains("Dubai, UAE")
                .contains("Specifically, responsibilities include the following");
    }

    @Test
    @DisplayName("all four sample documents parse to a non-trivial amount of text")
    void allFourFixturesParse() throws Exception {
        PositionDocumentTextReader reader = readerWith(40_000, 60);
        for (String name : new String[] {
                "Spec--General Manager - IT_vF.pdf", "JD_CEO.pdf", "Marketing Manager.pdf",
                "Chief Finanical Officer_Confidential_vDraft.pdf"}) {
            assertThat(reader.read(fixture(name)).length())
                    .as("expected %s to extract a real amount of text", name)
                    .isGreaterThan(500);
        }
    }

    @Test
    @DisplayName("a plain text file reads back verbatim")
    void readsPlainText() {
        String text = readerWith(40_000, 60).read("Job Title    CEO".getBytes(StandardCharsets.UTF_8));
        assertThat(text).isEqualTo("Job Title    CEO");
    }

    @Test
    @DisplayName("text is truncated to the configured character cap, regardless of format")
    void truncatesText() {
        String longText = "a".repeat(100);
        String text = readerWith(10, 60).read(longText.getBytes(StandardCharsets.UTF_8));
        assertThat(text).hasSize(10);
    }

    @Test
    @DisplayName("an encrypted PDF is refused by name, not parsed")
    void refusesAnEncryptedPdf() throws Exception {
        byte[] encrypted = encryptedBlankPdf();
        assertThatThrownBy(() -> readerWith(40_000, 60).read(encrypted))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ErrorCode.POSITION_DOCUMENT_UNREADABLE));
    }

    @Test
    @DisplayName("a PDF with no text layer is refused loudly, not returned as an empty answer")
    void refusesAPdfWithNoTextLayer() throws Exception {
        byte[] blank = blankPdf();
        assertThatThrownBy(() -> readerWith(40_000, 60).read(blank))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ErrorCode.POSITION_DOCUMENT_UNREADABLE));
    }

    @Test
    @DisplayName("a PDF over the configured page cap is refused whole, not read in part")
    void refusesAnOversizedPdf() throws Exception {
        byte[] threePages = blankPdf(3);
        assertThatThrownBy(() -> readerWith(40_000, 2).read(threePages))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ErrorCode.POSITION_DOCUMENT_UNREADABLE));
    }

    @Test
    @DisplayName("a legacy .doc, by its own OLE2 signature, is refused naming the fix")
    void refusesALegacyDoc() {
        byte[] ole2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0};
        assertThatThrownBy(() -> readerWith(40_000, 60).read(ole2))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.POSITION_DOCUMENT_UNREADABLE);
                    assertThat(e.getMessage()).contains(".docx").contains("PDF");
                });
    }

    @Test
    @DisplayName("a zip that is not a Word document is not mistaken for one by the raw zip signature")
    void aNonWordZipIsNotTreatedAsDocx() {
        // Every OOXML format shares the ZIP signature .docx is detected by — an .xlsx or .pptx reader
        // added later must not have its files claimed by DocxFormatReader on that signature alone.
        DocxFormatReader docx = new DocxFormatReader();
        assertThat(docx.supports(zipOf("xl/workbook.xml", "<workbook/>"))).isFalse();
        assertThat(docx.supports(zipOf("word/document.xml", "<document/>"))).isTrue();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static PositionDocumentTextReader readerWith(int maxCharacters, int maxPages) {
        PositionExtractionSettings settings =
                new PositionExtractionSettings(true, maxCharacters, maxPages, true, true);
        LightMoveProperties properties = new LightMoveProperties(null, null, null, null,
                new PositionSettings(null, settings), null, null, null, null, null, null, null);
        // The same order Spring's @Order annotations resolve to in production — see each reader's
        // own @Order — with the catch-all last, since it always answers supports() true.
        return new PositionDocumentTextReader(List.of(new PdfFormatReader(),
                new LegacyOfficeFormatReader(), new DocxFormatReader(), new PlainTextFormatReader()),
                properties);
    }

    private static byte[] zipOf(String entryName, String content) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(content.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] fixture(String name) throws Exception {
        try (var input = new ClassPathResource("documents/position/" + name).getInputStream()) {
            return input.readAllBytes();
        }
    }

    private static byte[] blankPdf() throws Exception {
        return blankPdf(1);
    }

    private static byte[] blankPdf(int pages) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] encryptedBlankPdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner-secret", "", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
