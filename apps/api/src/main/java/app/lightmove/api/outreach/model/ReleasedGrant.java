package app.lightmove.api.outreach.model;

/**
 * A grant our row no longer holds, kept just long enough to revoke it at the provider. {@code refreshToken} is
 * decrypted only for a direct grant whose provider revokes by the token itself (Google); null otherwise.
 */
public record ReleasedGrant(String grantId, String refreshToken) {

    @Override
    public String toString() {
        return "ReleasedGrant[grantId=" + grantId + ", refreshToken=<redacted>]";
    }
}
