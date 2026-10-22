package app.lightmove.api.notetaker.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifies a Google-signed ID token against Google's published keys: its issuer, that it was minted for our
 * {@code audience} (so a token meant for another service is no use here), its lifetime, and a verified email.
 */
public class GoogleBotIdentityVerifier implements BotIdentityVerifier {

    static final String GOOGLE_KEYS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder decoder;

    public GoogleBotIdentityVerifier(String audience) {
        this(NimbusJwtDecoder.withJwkSetUri(GOOGLE_KEYS).build(), audience);
    }

    GoogleBotIdentityVerifier(NimbusJwtDecoder decoder, String audience) {
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(),
                new JwtClaimValidator<String>(JwtClaimNames.ISS, GOOGLE_ISSUERS::contains),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audiences -> audiences != null && audiences.contains(audience)))));
        this.decoder = decoder;
    }

    @Override
    public Optional<String> serviceAccountOf(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException refused) {
            return Optional.empty();
        }
        return Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
                ? Optional.ofNullable(jwt.getClaimAsString("email")) : Optional.empty();
    }
}
