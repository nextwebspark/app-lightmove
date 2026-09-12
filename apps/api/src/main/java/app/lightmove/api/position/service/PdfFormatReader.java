package app.lightmove.api.position.service;

import app.lightmove.api.core.config.PositionExtractionSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * PDF, by its {@code %PDF} signature. Refuses an encrypted file or one over the configured page cap
 * rather than reading it in part, and always closes the {@link PDDocument} — parsing an untrusted PDF
 * in-process is an attack surface this feature is the first to open, and nothing here resolves an
 * external stream or an embedded form.
 */
@Component
@Order(100)
class PdfFormatReader implements PositionDocumentFormatReader {

    private static final byte[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46};

    @Override
    public boolean supports(byte[] content) {
        return FormatSignatures.startsWith(content, PDF_SIGNATURE);
    }

    @Override
    public String extractText(byte[] content, PositionExtractionSettings settings) {
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
}
