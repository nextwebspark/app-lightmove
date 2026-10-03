package app.lightmove.api.outreach.service;

/**
 * The provider refused a refresh token ({@code invalid_grant}: revoked, expired, or consent withdrawn). Only the
 * consultant reconnecting brings the mailbox back; trying again with the same token never will.
 */
public class RefreshTokenRefused extends RuntimeException {

    public RefreshTokenRefused(String providerError) {
        super(providerError);
    }
}
