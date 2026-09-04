package app.lightmove.api.candidate.model;

/**
 * A stored profile photo on its way out to the wire — bytes and the type they were downloaded as.
 *
 * <p>A record rather than the entity for the reason {@code StoredDocument} is one on the position
 * side: what a controller needs is the content, not a managed row with an identity and a version.
 */
public record StoredPhoto(byte[] content, String contentType) {}
