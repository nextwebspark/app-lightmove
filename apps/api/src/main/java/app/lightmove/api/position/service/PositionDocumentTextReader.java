package app.lightmove.api.position.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PositionExtractionSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

/**
 * Turns an attached position description's stored bytes into plain text.
 *
 * <p><b>The format is decided by the bytes' own signature, never the declared content type</b> — the
 * same rule {@code dataimport}'s {@code SpreadsheetReader} already established, for the same reason:
 * a claim made by whatever sent the request decides nothing on its own. A legacy {@code .doc} shares
 * its OLE2 signature with a legacy {@code .xls} and is always refused rather than parsed — reading it
 * needs {@code poi-scratchpad}, which is not on the classpath, and the fix a person can act on is to
 * save it as {@code .docx} or PDF.
 *
 * <p>Every cap here — pages, characters, no encrypted files, no empty text layer — exists because
 * parsing an untrusted PDF or {@code .docx} in-process is an attack surface this feature is the first
 * to open: a decompression bomb, a deeply nested object graph, an encrypted file whose password
 * prompt would otherwise hang. {@code PDDocument} is always closed, even on a thrown exception, and
 * nothing here resolves an external stream or an embedded form.
 */
@Service
public class PositionDocumentTextReader {

    private static final byte[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46};
    /** ZIP local-file header — every .docx (and .xlsx) is a zip; a legacy .doc shares .xls's OLE2 magic. */
    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2_SIGNATURE =
            {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private final PositionExtractionSettings settings;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public PositionDocumentTextReader(LightMoveProperties properties) {
        this.settings = properties.position().extraction();
    }

    public String read(byte[] content) {
        String text = extract(content);
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            throw ApiException.userFacing(ErrorCode.POSITION_DOCUMENT_UNREADABLE,
                    "This looks like a scanned image with no text layer. "
                            + "Save it as .docx or PDF, with a text layer, and try again.");
        }
        return trimmed.length() > settings.maxCharacters()
                ? trimmed.substring(0, settings.maxCharacters())
                : trimmed;
    }

    private String extract(byte[] content) {
        if (startsWith(content, PDF_SIGNATURE)) {
            return readPdf(content);
        }
        if (startsWith(content, OLE2_SIGNATURE)) {
            throw ApiException.userFacing(ErrorCode.POSITION_DOCUMENT_UNREADABLE,
                    "That's an old .doc file. Save it as .docx or PDF and upload that instead.");
        }
        if (startsWith(content, ZIP_SIGNATURE)) {
            return readDocx(content);
        }
        return readPlainText(content);
    }

    private String readPdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw ApiException.userFacing(ErrorCode.POSITION_DOCUMENT_UNREADABLE,
                        "That PDF is password-protected. Remove the password and try again.");
            }
            if (document.getNumberOfPages() > settings.maxPages()) {
                // Refused whole rather than read in part, matching IMPORT_TOO_MANY_ROWS's rule: taking
                // the first N pages would silently decide which half of the document mattered.
                throw ApiException.userFacing(ErrorCode.POSITION_DOCUMENT_UNREADABLE,
                        "That document has more than " + settings.maxPages() + " pages. "
                                + "Extraction only works on a mandate-length brief.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            // What makes a multi-column layout read in reading order instead of column-interleaved.
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (InvalidPasswordException e) {
            throw ApiException.userFacing(ErrorCode.POSITION_DOCUMENT_UNREADABLE,
                    "That PDF is password-protected. Remove the password and try again.");
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException apiException) {
                throw apiException;
            }
            throw new ApiException(ErrorCode.POSITION_DOCUMENT_UNREADABLE,
                    "PDF could not be opened: " + e.getMessage());
        }
    }

    /**
     * A {@code .docx} is a zip of XML parts, which is a zip-bomb vector on any reader — POI's own
     * {@code ZipSecureFile} ratio and entry-count limits are left at their shipped defaults
     * deliberately, rather than relaxed for a "trusted" upload that is, in fact, a stranger's file.
     */
    private String readDocx(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException | RuntimeException e) {
            throw new ApiException(ErrorCode.POSITION_DOCUMENT_UNREADABLE,
                    "docx could not be opened: " + e.getMessage());
        }
    }

    private static String readPlainText(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
