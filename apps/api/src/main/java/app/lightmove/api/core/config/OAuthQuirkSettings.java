package app.lightmove.api.core.config;

import java.util.List;

/**
 * Where an identity provider departs from the spec, named by registration id —
 * {@code lightmove.auth.oauth.*}.
 *
 * <p>This is config rather than code on purpose: a provider is a yml block here, and a
 * {@code if (linkedin)} in the sign-in path would be the one thing that undid that. A provider
 * with the same gap is another id in this list.
 */
public record OAuthQuirkSettings(
        /**
         * Registrations whose authorisation server does not implement PKCE.
         *
         * <p>Spring sends {@code code_challenge} to everyone and then {@code code_verifier} at
         * the token exchange. LinkedIn advertises no PKCE support in its discovery document and
         * answers the verifier with {@code invalid_client} — "client authentication failed",
         * which is a lie about the credentials and sends you hunting the wrong thing for an hour.
         */
        List<String> pkceUnsupportedRegistrations,

        /**
         * Registrations whose id_token does not echo the {@code nonce} we sent.
         *
         * <p>Spring then refuses the token with {@code invalid_nonce}. LinkedIn lists no
         * {@code nonce} in its discovery document's supported claims and does not return one.
         *
         * <p>What the nonce buys is a binding between the id_token and this browser's
         * authorisation request. Dropping it leaves the code exchange itself as that binding:
         * the code is single-use, redeemed server-to-server over TLS with our client secret,
         * and {@code state} still covers CSRF. Narrow, and only for a provider that gives no
         * choice.
         */
        List<String> nonceUnsupportedRegistrations,

        /**
         * Registrations trusted when they send no {@code email_verified} claim at all.
         *
         * <p>The claim is optional in OIDC, and Spring coerces anything present to a Boolean,
         * so "absent" is the only ambiguous case. Trusting absence by default would be a
         * standing account-takeover primitive the day someone adds a self-hosted IdP: this
         * service links a provider identity to an existing account on a matching email, so an
         * IdP that lets anyone claim {@code victim@firm.com} without proving the mailbox would
         * hand over that person's workspace. Providers that prove the address by other means —
         * LinkedIn mails it — opt in here, one reviewable line at a time.
         */
        List<String> emailVerifiedOptionalRegistrations
) {
    // Not @DefaultValue: on a List that binds the operator's "unset" to a *populated* list —
    // @DefaultValue("") yields [""], and an empty array is no clearer. See the blocklist trap.
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
