package app.lightmove.api.outreach.model;

/**
 * What a completed mailbox sign-in hands back: the gateway's handle for it, and whose it is.
 *
 * @param refreshToken     the provider's, from our own gateway only — null for a Nylas grant, which keeps its own.
 *                         Stored encrypted and never logged; {@link #toString()} leaves it out.
 * @param replyTextGranted whether the person granted reading what is written into our threads; false for a Nylas
 *                         grant, whose reach a setting states instead.
 * @param appClientId      the OAuth client our own gateway redeemed the sign-in through; null for a Nylas grant
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
