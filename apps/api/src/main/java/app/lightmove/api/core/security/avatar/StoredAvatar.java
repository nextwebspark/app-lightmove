package app.lightmove.api.core.security.avatar;

/** A stored photo as it is served. */
public record StoredAvatar(byte[] content, String contentType) {
}
