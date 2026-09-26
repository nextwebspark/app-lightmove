package app.lightmove.api.core.config;

import java.util.List;

/**
 * Where an identity provider departs from the spec, by registration id — {@code lightmove.auth.oauth.*}.
 * Config, not code: an {@code if (linkedin)} in the sign-in path would undo "a provider is a yml block".
 */
public record OAuthQuirkSettings(
        /** No PKCE: LinkedIn answers a {@code code_verifier} with a misleading {@code invalid_client}. */
        List<String> pkceUnsupportedRegistrations,

        /**
         * The id_token echoes no {@code nonce} (LinkedIn). Dropping it leaves the single-use code exchange
         * as the binding and {@code state} for CSRF; only for a provider that gives no choice.
         */
        List<String> nonceUnsupportedRegistrations,

        /**
         * Trusted when they send no {@code email_verified} claim. Never by default: sign-in links on a
         * matching email, so an IdP letting anyone claim {@code victim@firm.com} is an account takeover.
         */
        List<String> emailVerifiedOptionalRegistrations
) {
    // Not @DefaultValue: on a List it binds "unset" to a populated [""] — see PublicEmailDomains.
    public OAuthQuirkSettings {
        pkceUnsupportedRegistrations = pkceUnsupportedRegistrations == null
                ? List.of()
                : List.copyOf(pkceUnsupportedRegistrations);
        nonceUnsupportedRegistrations = nonceUnsupportedRegistrations == null
                ? List.of()
                : List.copyOf(nonceUnsupportedRegistrations);
        emailVerifiedOptionalRegistrations = emailVerifiedOptionalRegistrations == null
                ? List.of()
                : List.copyOf(emailVerifiedOptionalRegistrations);
    }
}
