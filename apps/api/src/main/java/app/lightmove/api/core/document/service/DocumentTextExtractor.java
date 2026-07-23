package app.lightmove.api.core.document.service;

/**
 * Plain text out of an uploaded document. The one port the brief-document upload needs from whatever
 * parsing library is behind it — kept file-format-agnostic so PDF, Word, and plain text are one call
 * site, not three.
 */
public interface DocumentTextExtractor {

    /**
     * The caller has already accepted {@code contentType} against its own allow-list — this is the
     * best-effort parse of bytes that claim to be that type.
     *
     * @throws app.lightmove.api.core.error.model.ApiException {@code BRIEF_EXTRACTION_FAILED} if the
     *         bytes cannot be parsed, or yield no usable text (e.g. a scanned, non-OCR'd PDF).
     */
    String extract(byte[] content, String contentType);
}
