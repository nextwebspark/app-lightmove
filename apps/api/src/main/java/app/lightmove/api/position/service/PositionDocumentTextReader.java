package app.lightmove.api.position.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PositionExtractionSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Turns an attached position description's stored bytes into plain text.
 *
 * <p><b>The format is decided by the bytes' own signature, never the declared content type</b> — the
 * same rule {@code dataimport}'s {@code SpreadsheetReader} already established, for the same reason:
 * a claim made by whatever sent the request decides nothing on its own.
 *
 * <p><b>Adding a format means writing a {@link PositionDocumentFormatReader}, not editing this
 * class.</b> Spring collects every bean of that type into {@code formatReaders}, tried in {@code
 * @Order}; the first whose {@link PositionDocumentFormatReader#supports} answers true wins, and
 * {@link PlainTextFormatReader}'s catch-all is ordered last so a real signature check always gets
 * first refusal. A {@code .pptx} or {@code .xlsx} reader — {@link PdfFormatReader}, {@link
 * DocxFormatReader} and {@link LegacyOfficeFormatReader} are the shipped examples to follow — is a
 * new class implementing that interface; this orchestrator does not change to gain one.
 *
 * <p>The caps enforced here — no empty text layer, the character ceiling — apply whichever format
 * answered, because a corrupted or image-only file of any format can equally extract to nothing.
 * Format-specific caps (a PDF's page count, a future format's sheet or slide count) are each reader's
 * own concern, checked inside its own {@code extractText}.
 */
@Service
public class PositionDocumentTextReader {

    private final List<PositionDocumentFormatReader> formatReaders;
    private final PositionExtractionSettings settings;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public PositionDocumentTextReader(List<PositionDocumentFormatReader> formatReaders,
                                      LightMoveProperties properties) {
        this.formatReaders = formatReaders;
        this.settings = properties.position().extraction();
    }

    public String read(byte[] content) {
        PositionDocumentFormatReader reader = formatReaders.stream()
                .filter(candidate -> candidate.supports(content))
                .findFirst()
                // Unreachable in practice — PlainTextFormatReader answers every signature — but a
                // caller must not see a null-pointer if that catch-all is ever misconfigured away.
                .orElseThrow(() -> ApiException.of(ErrorCode.POSITION_DOCUMENT_UNREADABLE));

        String trimmed = reader.extractText(content, settings).strip();
        if (trimmed.isEmpty()) {
            throw ApiException.userFacing(ErrorCode.POSITION_DOCUMENT_UNREADABLE,
                    "This looks like a scanned image with no text layer. "
                            + "Save it as .docx or PDF, with a text layer, and try again.");
        }
        return trimmed.length() > settings.maxCharacters()
                ? trimmed.substring(0, settings.maxCharacters())
                : trimmed;
    }
}
