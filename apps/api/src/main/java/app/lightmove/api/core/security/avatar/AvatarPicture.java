package app.lightmove.api.core.security.avatar;

/** A picture as we store it: re-encoded square JPEG bytes and their SHA-256. */
record AvatarPicture(byte[] jpeg, String sha256) {

    static final String CONTENT_TYPE = "image/jpeg";
}
