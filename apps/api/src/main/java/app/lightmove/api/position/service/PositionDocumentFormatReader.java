package app.lightmove.api.position.service;

import app.lightmove.api.core.config.PositionExtractionSettings;

/**
 * One document format's byte-signature check and text extraction. A new format is a new
 * {@code @Component} ordered ahead of {@link PlainTextFormatReader}'s catch-all.
 */
interface PositionDocumentFormatReader {

    boolean supports(byte[] content);

    /** Called only after {@link #supports}; throws {@code POSITION_DOCUMENT_UNREADABLE} naming the problem. */
    String extractText(byte[] content, PositionExtractionSettings settings);
}
