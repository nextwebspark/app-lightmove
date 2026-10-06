package app.lightmove.api.core.security.oauth;

/** Why a client id metadata document was not used. Only an outage may let an older copy keep serving. */
public enum ClientMetadataRefusal {
    NOT_HTTPS(false),
    PRIVATE_ADDRESS(false),
    REDIRECT(false),
    TOO_LARGE(false),
    NOT_JSON(false),
    INVALID_DOCUMENT(false),
    TIMEOUT(true),
    HTTP_ERROR(true),
    UNREACHABLE(true);

    private final boolean outage;

    ClientMetadataRefusal(boolean outage) {
        this.outage = outage;
    }

    /** The host could not answer, as against answering with something we will not take. */
    public boolean isOutage() {
        return outage;
    }
}
