package app.lightmove.api.outreach.model;

/**
 * What a completed mailbox sign-in hands back. {@code refreshToken} is ours alone (null for Nylas), stored encrypted
 * and left out of {@link #toString()}; {@code replyTextGranted} and {@code appClientId} are false and null for Nylas.
 */
public record GrantedMailbox(String grantId, String address, String provider, String refreshToken,
                             boolean replyTextGranted, String appClientId) {

    public GrantedMailbox(String grantId, String address, String provider, String refreshToken,
                          boolean replyTextGranted) {
        this(grantId, address, provider, refreshToken, replyTextGranted, null);
    }

    public GrantedMailbox(String grantId, String address, String provider) {
        this(grantId, address, provider, null, false);
    }

    public GrantedMailbox(String grantId, String address, String provider, String refreshToken) {
        this(grantId, address, provider, refreshToken, false);
    }

    @Override
    public String toString() {
        return "GrantedMailbox[grantId=" + grantId + ", address=" + address + ", provider=" + provider
                + ", refreshToken=" + (refreshToken == null ? "null" : "<redacted>")
                + ", replyTextGranted=" + replyTextGranted + ", appClientId=" + appClientId + "]";
    }
}
