package app.lightmove.api.position.service;

import app.lightmove.api.core.config.PositionExtractionSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The legacy binary Office family — {@code .doc}, {@code .xls}, {@code .ppt} — sharing one OLE2
 * compound-file signature this reader does not tell apart, because doing so needs parsing the file's
 * own storage directory for a stream name ({@code WordDocument}, {@code Workbook}, {@code PowerPoint
 * Document}) and nothing here does that yet. Always refused rather than guessed at: reading one needs
 * {@code poi-scratchpad}, which is not on the classpath, and the fix a person can act on is to save it
 * as a modern format.
 */
@Component
@Order(200)
class LegacyOfficeFormatReader implements PositionDocumentFormatReader {

    private static final byte[] OLE2_SIGNATURE =
            {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    @Override
    public boolean supports(byte[] content) {
        return FormatSignatures.startsWith(content, OLE2_SIGNATURE);
    }

    @Override
    public String extractText(byte[] content, PositionExtractionSettings settings) {
        throw ApiException.userFacing(ErrorCode.POSITION_DOCUMENT_UNREADABLE,
                "That's an old Office file (.doc, .xls or .ppt). Save it as .docx or PDF and upload "
                        + "that instead.");
    }
}
