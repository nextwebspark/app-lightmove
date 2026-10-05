package app.lightmove.api.outreach.model;

/**
 * A grant our row no longer holds, kept just long enough to revoke it at the provider. {@code refreshToken} is
 * decrypted only where the gateway revokes by the token itself ({@link
 * app.lightmove.api.outreach.service.MailboxGateway#revokesByRefreshToken}); null otherwise, and null when the same
 * mailbox reconnected, whose new token a revoke of the old one would take down with it.
 */
public record ReleasedGrant(String grantId, String refreshToken) {

    @Override
    public String toString() {
        return "ReleasedGrant[grantId=" + grantId + ", refreshToken=<redacted>]";
    }
}
