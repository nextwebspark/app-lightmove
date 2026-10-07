package app.lightmove.api.core.security.oauth;

/** A client id metadata document that could not be fetched, or was fetched and refused. */
public class ClientMetadataUnavailable extends RuntimeException {

    private final ClientMetadataRefusal refusal;

    public ClientMetadataUnavailable(ClientMetadataRefusal refusal, String detail) {
        super(refusal + ": " + detail);
        this.refusal = refusal;
    }

    public ClientMetadataUnavailable(ClientMetadataRefusal refusal, String detail, Throwable cause) {
        super(refusal + ": " + detail, cause);
        this.refusal = refusal;
    }

    public ClientMetadataRefusal refusal() {
        return refusal;
    }
}
