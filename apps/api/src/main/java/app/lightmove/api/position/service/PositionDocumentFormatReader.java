package app.lightmove.api.position.service;

import app.lightmove.api.core.config.PositionExtractionSettings;

/**
 * One document format's byte-signature check and text extraction — the plug point a new format joins
 * through. Spring collects every bean of this type into the list {@link PositionDocumentTextReader}
 * tries in {@code @Order}, so adding a format (a {@code .pptx}, an {@code .xlsx}) is a new class
 * implementing this interface and {@code @Component}-annotated, ordered ahead of {@link
 * PlainTextFormatReader}'s catch-all — no change to the orchestrator itself.
 *
 * <p>{@link #supports} decides from the bytes' own signature, never a declared content type — see
 * {@link PositionDocumentTextReader}'s class doc for why. A format sure enough of its signature to
 * always refuse it — a legacy binary format nothing on the classpath can parse — still implements
 * this: {@link #extractText} throws instead of returning text, naming the fix. See {@link
 * LegacyOfficeFormatReader}.
 */
interface PositionDocumentFormatReader {

    /** Whether this reader recognises the document's own byte signature. */
    boolean supports(byte[] content);

    /**
     * Extracts this document's text, or throws a {@code POSITION_DOCUMENT_UNREADABLE} naming the
     * problem. Called only when {@link #supports} has already answered true. {@code settings} is
     * handed to every reader uniformly, even one with nothing of its own to check there yet, so a
     * future format's own cap (a sheet-row limit, a slide-count limit) needs no change to this
     * interface or to the orchestrator that calls it.
     */
    String extractText(byte[] content, PositionExtractionSettings settings);
}
