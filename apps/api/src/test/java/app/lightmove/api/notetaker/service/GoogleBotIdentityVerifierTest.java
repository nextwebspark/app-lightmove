package app.lightmove.api.notetaker.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class GoogleBotIdentityVerifierTest {

    private static final String AUDIENCE = "https://beta.uncava.com";
    private static final String BOT = "meetingbot@uncava-prod.iam.gserviceaccount.com";
    private static RSAKey google;
    private static RSAKey stranger;

    @BeforeAll
    static void keys() throws JOSEException {
        google = new RSAKeyGenerator(2048).keyID("google").generate();
        stranger = new RSAKeyGenerator(2048).keyID("stranger").generate();
    }

    @Test
    @DisplayName("a Google-signed, live ID token for our audience names its verified service account")
    void readsTheServiceAccount() throws JOSEException {
        assertThat(verifier().serviceAccountOf(token(google, "https://accounts.google.com", AUDIENCE, true, 600)))
                .contains(BOT);
        assertThat(verifier().serviceAccountOf(token(google, "accounts.google.com", AUDIENCE, true, 600)))
                .contains(BOT);
    }

    @Test
    @DisplayName("another signer, issuer or audience, an expired token or an unverified email names nobody")
    void refusesTheRest() throws JOSEException {
        assertThat(verifier().serviceAccountOf(token(stranger, "https://accounts.google.com", AUDIENCE, true, 600)))
                .isEmpty();
        assertThat(verifier().serviceAccountOf(token(google, "https://evil.example", AUDIENCE, true, 600))).isEmpty();
        assertThat(verifier().serviceAccountOf(token(google, "https://accounts.google.com", "https://other.example",
                true, 600))).isEmpty();
        assertThat(verifier().serviceAccountOf(token(google, "https://accounts.google.com", AUDIENCE, true, -600)))
                .isEmpty();
        assertThat(verifier().serviceAccountOf(token(google, "https://accounts.google.com", AUDIENCE, false, 600)))
                .isEmpty();
        assertThat(verifier().serviceAccountOf("not-a-token")).isEmpty();
    }

    private static GoogleBotIdentityVerifier verifier() throws JOSEException {
        return new GoogleBotIdentityVerifier(NimbusJwtDecoder.withPublicKey(google.toRSAPublicKey()).build(), AUDIENCE);
    }

    private static String token(RSAKey key, String issuer, String audience, boolean verified, long expiresInSeconds)
            throws JOSEException {
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .audience(audience)
                        .subject("1234567890")
                        .claim("email", BOT)
                        .claim("email_verified", verified)
                        .issueTime(Date.from(now.minusSeconds(60)))
                        .expirationTime(Date.from(now.plusSeconds(expiresInSeconds)))
                        .build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
