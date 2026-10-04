package app.lightmove.api.position.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PositionExtractionSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Turns an attached position description's stored bytes into plain text, through the first
 * {@link PositionDocumentFormatReader} (in {@code @Order}) that supports them.
 *
 * <p>The format is decided by the bytes' own signature, never the declared content type.
 */
@Service
public class PositionDocumentTextReader {

    private final List<PositionDocumentFormatReader> formatReaders;
    private final PositionExtractionSettings settings;

    public PositionDocumentTextReader(List<PositionDocumentFormatReader> formatReaders,
                                      LightMoveProperties properties) {
        this.formatReaders = formatReaders;
        this.settings = properties.position().extraction();
    }

    public String read(byte[] content) {
        PositionDocumentFormatReader reader = formatReaders.stream()
                .filter(candidate -> candidate.supports(content))
                .findFirst()
                .orElseThrow(() -> ApiException.of(ErrorCode.POSITION_DOCUMENT_UNREADABLE));

        String trimmed = reader.extractText(content, settings).strip();
        if (trimmed.isEmpty()) {
            throw ApiException.userFacing(ErrorCode.POSITION_DOCUMENT_UNREADABLE,
                    "This looks like a scanned image with no text layer. "
                            + "Save it as .docx or PDF, with a text layer, and try again.");
        }
        if (trimmed.length() > settings.maxCharacters()) {
            // Refused whole: taking the first N characters would silently decide what mattered.
            throw ApiException.userFacing(ErrorCode.POSITION_DOCUMENT_UNREADABLE,
                    "That document has more than " + settings.maxCharacters() + " characters. "
                            + "Extraction only works on a mandate-length brief.");
        }
        return trimmed;
    }
}
