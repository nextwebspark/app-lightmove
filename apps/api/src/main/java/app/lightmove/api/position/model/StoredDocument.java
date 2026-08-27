package app.lightmove.api.position.model;

/** A stored file on its way back out: the bytes plus what the browser needs to save them. */
public record StoredDocument(String fileName, String contentType, byte[] content) {
}
