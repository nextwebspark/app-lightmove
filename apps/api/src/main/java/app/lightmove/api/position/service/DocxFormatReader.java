package app.lightmove.api.position.service;

import app.lightmove.api.core.config.PositionExtractionSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@code .docx}, by the ZIP signature every OOXML format shares plus the one entry ({@code
 * word/document.xml}) that is specifically Word's. Checked rather than assumed from the raw zip
 * signature alone — an {@code .xlsx} ({@code xl/workbook.xml}) or {@code .pptx} ({@code
 * ppt/presentation.xml}) reader added beside this one shares that same signature, and would be
 * mistaken for a Word document without this check.
 *
 * <p>A zip is a zip-bomb vector on any reader — POI's own {@code ZipSecureFile} ratio and entry-count
 * limits are left at their shipped defaults deliberately, rather than relaxed for a "trusted" upload
 * that is, in fact, a stranger's file.
 */
@Component
@Order(300)
class DocxFormatReader implements PositionDocumentFormatReader {

    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};
    private static final String WORD_DOCUMENT_ENTRY = "word/document.xml";

    /**
     * Bounds on the entry scan itself — {@code ZipInputStream#getNextEntry} inflates the current
     * entry in full to reach the next one, before POI's {@code ZipSecureFile} limits (which only
     * guard {@link #extractText}) ever run. A file failing either ceiling is answered {@code false}
     * rather than read further.
     */
    private static final int MAX_ENTRIES = 1_000;

    private static final long MAX_INFLATED_BYTES = 64L * 1024 * 1024;

    @Override
    public boolean supports(byte[] content) {
        return FormatSignatures.startsWith(content, ZIP_SIGNATURE) && hasEntry(content, WORD_DOCUMENT_ENTRY);
    }

    @Override
    public String extractText(byte[] content, PositionExtractionSettings settings) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException | RuntimeException e) {
            throw new ApiException(ErrorCode.POSITION_DOCUMENT_UNREADABLE,
                    "docx could not be opened: " + e.getMessage());
        }
    }

    /**
     * A cheap streaming scan of the zip's entry names — no need to fully open it as an OOXML package.
     *
     * <p>Bounded on its own terms: each entry's bytes are read (and counted) explicitly rather than
     * left to {@code getNextEntry}'s implicit drain, so a zip bomb is caught mid-inflation instead of
     * fully decompressed before either ceiling is checked.
     */
    private static boolean hasEntry(byte[] content, String entryName) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            byte[] buffer = new byte[8192];
            long inflatedBytes = 0;
            int entries = 0;
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > MAX_ENTRIES) {
                    return false;
                }
                if (entry.getName().equals(entryName)) {
                    return true;
                }
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    inflatedBytes += read;
                    if (inflatedBytes > MAX_INFLATED_BYTES) {
                        return false;
                    }
                }
            }
            return false;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
