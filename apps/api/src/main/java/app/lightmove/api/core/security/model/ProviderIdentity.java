package app.lightmove.api.core.security.model;

/**
 * Who an identity provider proved someone to be, read from standard OIDC claims only: the registration id uppercased,
 * the provider's subject, the verified address lower-cased, a display name, and a picture worth fetching or null.
 */
public record ProviderIdentity(String provider, String subject, String email, String fullName, String pictureUrl) {

    @Override
    public String toString() {
        return "ProviderIdentity[provider=%s]".formatted(provider);
    }
}
