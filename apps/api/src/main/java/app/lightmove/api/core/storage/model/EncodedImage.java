package app.lightmove.api.core.storage.model;

/** A picture after {@code EmailImageEncoder} has decoded, resized and written it again: these bytes are all that is kept. */
public record EncodedImage(byte[] content, String contentType, int width, int height) {}
